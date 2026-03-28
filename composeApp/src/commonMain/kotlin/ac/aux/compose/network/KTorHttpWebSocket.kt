package ac.aux.compose.network

import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readReason
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class KTorHttpWebSocket(
    val url: NormalizedRelayUrl,
    val httpClientBuilder: (url: NormalizedRelayUrl) -> HttpClient,
    val ktorWebsocketListener: KtorWebsocketListener
) : WebSocket {
    val scope = CoroutineScope(Dispatchers.IO)

    val logger = Logger.withTag("KTorHttpWebSocket")

    private var httpClient: HttpClient? = null

    private var webSocketSession: DefaultClientWebSocketSession? = null
    private var outgoingChannel: SendChannel<Frame>? = null

    override fun needsReconnect(): Boolean {
        logger.d("needsReconnect ${url.url}")
        if (webSocketSession == null) return true

        val activeHttpClient = httpClient ?: return true

        val currentHttpClient = httpClientBuilder(url)

        // TODO: Proxy...

        // TODO: timeout checks

        return false
    }

    override fun connect() {
        logger.d("connect ${url.url}")
        runBlocking(
            scope.coroutineContext
        ) {
            httpClient = httpClientBuilder(url)

            webSocketSession = httpClient?.webSocketSession(
                urlString = url.url
            )

            val job = scope.launch {
                webSocketSession?.incoming?.consumeAsFlow()?.collect { frame ->
                    when(frame) {
                        is Frame.Text -> {
                            frame.readText().let {
                                logger.d("WebSocket Read Text: $it")
                            }
                            webSocketSession?.close()
                        }
                        is Frame.Close -> {
                            logger.d("Close $frame")
                        }
                        is Frame.Ping -> {
                            logger.d("Ping: $frame")
                        }
                        is Frame.Pong -> {
                            logger.d("Pong: $frame")
                        }
                        is Frame.Binary -> {
                            logger.d("Binary Message: $frame")
                        }
                        else -> {
                            logger.d("Unsupported frame: $frame")
                        }
                    }
                }


                logger.i("Websocket exit")
            }

            job.join()
            ktorWebsocketListener.onClosed(0, "Done")
//            webSocketSession = httpClient?.webSocketSession(
//                urlString = url.url
//            ) {
//                logger.d("webSocketSession")
//            }
//
//            webSocketSession?.let { socketSession ->
//
//                logger.d("isOpen : ${socketSession.isActive}")
//                out.onOpen(
//                    pingMillis = 1,
//                    compression = false
//                )
//
//                val incomingFrameJob = scope.launch {
//                    try {
//                        for (frame in socketSession.incoming) {
//                            when (frame) {
//                                is Frame.Text -> {
//                                    frame.readText().let {
//                                        logger.d("WebSocket Read Text: $it")
//                                        out.onMessage(it)
//                                    }
//                                }
//                                is Frame.Close -> {
//                                    logger.d("Close $frame")
//                                    val reason = frame.readReason()
//                                    out.onClosed(
//                                        code = reason?.code?.toInt() ?: 0,
//                                        reason = reason?.knownReason.toString()
//                                    )
//                                }
//                                is Frame.Ping -> {
//                                    logger.d("Ping: $frame")
//                                }
//                                is Frame.Pong -> {
//                                    logger.d("Pong: $frame")
//                                }
//                                is Frame.Binary -> {
//                                    logger.d("Binary Message: $frame")
//                                }
//                                else -> {
//                                    logger.d("Unsupported frame: $frame")
//                                }
//                            }
//                        }
//                    } catch (e: Throwable) {
//                        logger.e("Websocket error: ", e)
//                    } finally {
//                        logger.d("Closed for real... hopefully we know the reason")
//                    }
//                }
//
//                incomingFrameJob.join()
//                logger.d("webSocketSession closed")
//            }
        }

    }

    override fun disconnect() {
        logger.d("disconnect ${url.url}")
        runBlocking(
            scope.coroutineContext
        ) {
            webSocketSession?.close()

        }
    }

    override fun send(msg: String): Boolean = try {
        logger.d("Send ${url.url}: $msg")
        runBlocking(
            scope.coroutineContext
        ) {
            webSocketSession?.send(
                Frame.Text(msg)
            )

            return@runBlocking true
        }
    } catch(e: Throwable) {
        logger.e("Failed to send $msg", e)
        return false
    }

    class Builder(
        val ktorWebsocketListener: KtorWebsocketListener = KtorWebsocketListener(),
        val httpClientBuilder: (NormalizedRelayUrl) -> HttpClient,
    ): WebsocketBuilder {
        val logger = Logger.withTag("KtorWebsocketBuilder")

        override fun build(
            url: NormalizedRelayUrl,
            out: WebSocketListener
        ): WebSocket {
            ktorWebsocketListener.out = out
            logger.d("Build KTorHttpWebSocket: ${url.url}")

            return KTorHttpWebSocket(
                url = url,
                httpClientBuilder = httpClientBuilder,
                ktorWebsocketListener = ktorWebsocketListener
            )
        }
    }
}