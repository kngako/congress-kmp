package ac.cord.auxiliary.compose.network.sockets

import ac.cord.auxiliary.compose.exceptions.NetworkException
import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readReason
import io.ktor.websocket.readText
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import okio.Buffer
import okio.GzipSink
import okio.Inflater
import okio.InflaterSource
import okio.buffer
import okio.use
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
internal class NostrSocketClientImpl(
    wssUrl: String,
    private val httpClient: HttpClient,
    private val incomingCompressionEnabled: Boolean = false,
    private val onSocketConnectionOpened: SocketConnectionOpenedCallback? = null,
    private val onSocketConnectionClosed: SocketConnectionClosedCallback? = null,
) : NostrSocketClient {

    val logger = Logger.withTag("NostrSocketClientImpl")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val wsMutex = Mutex()
    private var wsSession: WebSocketSession? = null
    private var wsReceiverJob: Job? = null

    private val _incomingMessages = MutableSharedFlow<NostrIncomingMessage>()
    override val incomingMessages = _incomingMessages.asSharedFlow()

    override val socketUrl = wssUrl.cleanWebSocketUrl()

    override suspend fun ensureSocketConnectionOrThrow() {
        if (wsSession != null && wsSession?.isActive == true) return

        wsMutex.withLock {
            if (wsSession == null || wsSession?.isActive == false) {
                wsSession = acquireWebSocketSession(url = socketUrl)
            }
        }
    }

    private suspend fun acquireWebSocketSession(url: String): WebSocketSession {
        return try {
            httpClient.webSocketSession(urlString = url).apply {
                launchWebSocketReceiver()
                onSocketConnectionOpened?.invoke(url)
                if (incomingCompressionEnabled) {
                    val id = Uuid.generateV4().toHexDashString()
                    sendMESSAGE(
                        text = """["REQ","$id",{"cache":["set_primal_protocol",{"compression":"zlib"}]}]""",
                        ensureSessionBeforeSend = false,
                    )
                }
            }
        } catch (error: Exception) {
            logger.w("NostrSocketClient::acquireWebSocketSession($socketUrl) failed.", error)
            close()
            onSocketConnectionClosed?.invoke(socketUrl, error)
            throw NetworkException(cause = error)
        }
    }

    private fun WebSocketSession.launchWebSocketReceiver() {
        wsReceiverJob?.cancel()
        wsReceiverJob = scope.launch {
            receiveSocketMessages()
        }
    }

    private suspend fun WebSocketSession.receiveSocketMessages() {
        try {
            for (frame in incoming) {
                when (frame) {
                    is Frame.Text -> {
                        val text = frame.readText()
                        logLargeText(text = text, url = socketUrl, incoming = true)
                        processIncomingMessage(text = text)
                    }

                    is Frame.Binary -> {
                        val decompressedMessage = decompressMessage(frame.data)
                        logLargeText(text = decompressedMessage, url = socketUrl, incoming = true)
                        processIncomingMessage(text = decompressedMessage)
                    }

                    is Frame.Close -> {
                        val closeReason = frame.readReason()
                        logger.w { "WS $socketUrl closed. [${closeReason?.code}, ${closeReason?.message}]" }
                        close()
                        onSocketConnectionClosed?.invoke(socketUrl, null)
                    }

                    else -> Unit
                }
            }
        } catch (error: CancellationException) {
            logger.w("NostrSocketClient::receiveSocketMessages() on $socketUrl cancelled.")
            throw error
        } catch (error: Exception) {
            logger.w("NostrSocketClient::receiveSocketMessages() on $socketUrl failed.", error)
            close()
            onSocketConnectionClosed?.invoke(socketUrl, error)
        }
    }

    override suspend fun close() {
        wsReceiverJob?.cancel()
        wsReceiverJob = null
        runCatching {
            wsSession?.close(
                reason = CloseReason(
                    code = CloseReason.Codes.NORMAL,
                    message = "Closed by client.",
                ),
            )
        }
        wsSession = null
    }

    private fun processIncomingMessage(text: String) {
        text.parseIncomingMessage()?.let {
            scope.launch {
                if (it is NostrIncomingMessage.EoseMessage) {
                    delay(75.milliseconds)
                }
                _incomingMessages.emit(value = it)
            }
        }
    }

    override suspend fun sendMESSAGE(text: String, ensureSessionBeforeSend: Boolean) {
        if (ensureSessionBeforeSend) {
            ensureSocketConnectionOrThrow()
        }
        logLargeText(text = text, url = socketUrl, incoming = false)
        wsSession?.send(Frame.Text(text = text))
    }

    override suspend fun sendREQ(subscriptionId: String, data: JsonObject) {
        val reqMessage = data.buildNostrREQMessage(subscriptionId)
        return sendMESSAGE(text = reqMessage)
    }

    override suspend fun sendCOUNT(data: JsonObject): String {
        val subscriptionId: String = Uuid.generateV4().toHexDashString()
        val reqMessage = data.buildNostrCOUNTMessage(subscriptionId)
        sendMESSAGE(text = reqMessage)
        return subscriptionId
    }

    override suspend fun sendCLOSE(subscriptionId: String) = sendMESSAGE(text = subscriptionId.buildNostrCLOSEMessage())

    override suspend fun sendEVENT(signedEvent: JsonObject) = sendMESSAGE(text = signedEvent.buildNostrEVENTMessage())

    override suspend fun sendAUTH(signedEvent: JsonObject) = sendMESSAGE(text = signedEvent.buildNostrAUTHMessage())

    private fun logLargeText(
        text: String,
        url: String,
        incoming: Boolean,
    ) {
        val chunks = text.chunked(size = 3_500)
        val chunksCount = chunks.size
        chunks.forEachIndexed { index, chunk ->
            val prefix = if (incoming) "<--" else "-->"
            val suffix = if (index == chunksCount - 1) "[$url]" else ""
            logger.d(
                "$prefix $chunk $suffix",
            )
        }
    }

    @Suppress("unused")
    private fun compressMessage(message: String): ByteArray {
        val buffer = Buffer()
        GzipSink(buffer).buffer().use { sink ->
            sink.writeUtf8(message)
            sink.flush() // Ensure all data is written
        }
        return buffer.readByteArray()
    }

    private fun decompressMessage(compressedMessage: ByteArray): String {
        val buffer = Buffer().write(compressedMessage)
        InflaterSource(buffer, Inflater(false)).buffer().use { source ->
            return source.readUtf8()
        }
    }

    private fun String.cleanWebSocketUrl(): String {
        return replace("https://", "wss://", ignoreCase = true)
            .replace("http://", "ws://", ignoreCase = true)
            .let { if (it.endsWith("/")) it.dropLast(1) else it }
    }
}
