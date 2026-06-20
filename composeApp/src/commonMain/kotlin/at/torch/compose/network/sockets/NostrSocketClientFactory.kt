package at.torch.compose.network.sockets

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import kotlinx.serialization.json.Json

internal val defaultSocketsHttpClient by lazy {

    HttpClient {
        install(WebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(Json {
                isLenient = true
                ignoreUnknownKeys = true
            })
            pingIntervalMillis = 20_000
        }
    }
}


object NostrSocketClientFactory {

    fun create(
        wssUrl: String,
        httpClient: HttpClient,
        incomingCompressionEnabled: Boolean = false,
        onSocketConnectionOpened: SocketConnectionOpenedCallback? = null,
        onSocketConnectionClosed: SocketConnectionClosedCallback? = null,
    ): NostrSocketClient {
        return NostrSocketClientImpl(
            httpClient = httpClient,
            wssUrl = wssUrl,
            incomingCompressionEnabled = incomingCompressionEnabled,
            onSocketConnectionOpened = onSocketConnectionOpened,
            onSocketConnectionClosed = onSocketConnectionClosed,
        )
    }

    fun create(
        wssUrl: String,
        incomingCompressionEnabled: Boolean = false,
        onSocketConnectionOpened: SocketConnectionOpenedCallback? = null,
        onSocketConnectionClosed: SocketConnectionClosedCallback? = null,
    ) = create(
        httpClient = defaultSocketsHttpClient,
        wssUrl = wssUrl,
        incomingCompressionEnabled = incomingCompressionEnabled,
        onSocketConnectionOpened = onSocketConnectionOpened,
        onSocketConnectionClosed = onSocketConnectionClosed,
    )
}
