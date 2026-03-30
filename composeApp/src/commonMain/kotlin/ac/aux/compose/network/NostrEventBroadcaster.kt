package ac.aux.compose.network

import ac.aux.compose.database.model.BroadcastNostrEventReceipt
import ac.aux.compose.database.model.BroadcastNostrEventRequest
import ac.aux.compose.database.model.intermdiate.LocalBroadcastNostrEventRequest
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.utils.text
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray

class NostrEventBroadcaster(
    val scope: CoroutineScope,

) {
    private val logger = Logger.withTag("NostrEventBroadcaster")

    private val broadcastingJobs = mutableMapOf<String, Job>()

    val httpClient = HttpClient() {
        install(WebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(Json {
                isLenient = true
                ignoreUnknownKeys = true
            })
            pingIntervalMillis = 20_000
        }
    }

    fun broadcastEvent(
        localBroadcastNostrEventRequest: LocalBroadcastNostrEventRequest,
        onBroadcastRequestProcessed: (BroadcastNostrEventRequest) -> Unit,
        onBroadcastReceipt: (BroadcastNostrEventReceipt) -> Unit
    ) {
        val event: Event = localBroadcastNostrEventRequest.nostrEvent.let { nostrEvent ->
            Event(
                id = nostrEvent.id,
                kind = nostrEvent.kind,
                pubKey = nostrEvent.pubKey,
                content = nostrEvent.content,
                tags = nostrEvent.tags,
                sig = nostrEvent.sig,
                createdAt = nostrEvent.createdAt.epochSeconds
            )
        }

        scope.launch {
            val webSocketSession = httpClient.webSocketSession(
                urlString = localBroadcastNostrEventRequest.broadcastNostrEventRequest.relayURL
            )

            broadcastingJobs[localBroadcastNostrEventRequest.broadcastNostrEventRequest.relayURL] = scope.launch {
                val message = OptimizedJsonMapper.toJson(
                    EventCmd(
                        event
                    )
                )
                logger.d("Broadcasting (${localBroadcastNostrEventRequest.broadcastNostrEventRequest.relayURL}): $message")
                webSocketSession.send(
                    frame = Frame.Text(message)
                )

                onBroadcastRequestProcessed.invoke(localBroadcastNostrEventRequest.broadcastNostrEventRequest)

                webSocketSession.incoming.consumeAsFlow().collect { frame ->
                    when(frame) {
                        is Frame.Text -> {
                            frame.readText().let { text ->
                                try {
                                    logger.d("WebSocket Read Text: $text")
                                    val result = Json.decodeFromString<JsonArray>(text)

                                    if (result.firstOrNull()?.text == "OK") {
                                        result.getOrNull(1)?.text?.let { eventId ->
                                            if (eventId == localBroadcastNostrEventRequest.broadcastNostrEventRequest.nostrEventId) {

                                                onBroadcastReceipt.invoke(
                                                    BroadcastNostrEventReceipt(
                                                        nostrEventId = localBroadcastNostrEventRequest.broadcastNostrEventRequest.nostrEventId,
                                                        unsignedNostrEventId = localBroadcastNostrEventRequest.broadcastNostrEventRequest.unsignedNostrEventId,
                                                        messages = result.getOrNull(3)?.text,
                                                        isAccepted = result.getOrNull(2)?.text == "true",
                                                        relayURL = localBroadcastNostrEventRequest.broadcastNostrEventRequest.relayURL
                                                    )
                                                )
                                            }
                                        }
                                    }
                                } catch (e: Throwable) {
                                    logger.e("Error processing receipt: $text", e)
                                }
                            }
                            webSocketSession.close()
                            broadcastingJobs.remove(localBroadcastNostrEventRequest.broadcastNostrEventRequest.relayURL)?.let { job ->
                                job.cancel()
                            }
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

            broadcastingJobs[localBroadcastNostrEventRequest.broadcastNostrEventRequest.relayURL]?.join()
        }
    }
}