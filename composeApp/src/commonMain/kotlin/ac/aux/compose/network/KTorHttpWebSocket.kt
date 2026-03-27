package ac.aux.compose.network

import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class KTorHttpWebSocket(
    val url: NormalizedRelayUrl,
    val httpClientBuilder: (url: NormalizedRelayUrl) -> HttpClient,
    val out: WebSocketListener
) : WebSocket {
    val scope = CoroutineScope(Dispatchers.IO)

    val logger = Logger.withTag("KTorHttpWebSocket")

    private var httpClient: HttpClient? = null

    private var webSocketSession: DefaultClientWebSocketSession? = null

    override fun needsReconnect(): Boolean {
        TODO("Not yet implemented")
    }

    override fun connect() {
        runBlocking {
            httpClient = httpClientBuilder(url)
            webSocketSession = httpClient?.webSocketSession(
                urlString = url.url
            ) {

            }

            webSocketSession?.let { socketSession ->

                val job = scope.launch {
                    for (frame in socketSession.incoming) {
                        val frameText = frame as? Frame.Text

                        frameText?.readText()?.let { out.onMessage(it) }
                    }
                }

                job.join()

            }

        }

    }

    override fun disconnect() {
        runBlocking {
            webSocketSession?.close()
        }
    }

    override fun send(msg: String): Boolean = try {
        runBlocking {

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
        val httpClientBuilder: (NormalizedRelayUrl) -> HttpClient
    ): WebsocketBuilder {
        override fun build(
            url: NormalizedRelayUrl,
            out: WebSocketListener
        ): WebSocket = KTorHttpWebSocket(
            url = url,
            httpClientBuilder = httpClientBuilder,
            out = out
        )
    }

}