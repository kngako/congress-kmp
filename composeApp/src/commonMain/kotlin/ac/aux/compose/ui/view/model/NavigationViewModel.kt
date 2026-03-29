package ac.aux.compose.ui.view.model

import ac.aux.compose.database.model.BroadcastNostrEventReceipt
import ac.aux.compose.database.model.NostrEvent
import ac.aux.compose.managers.SeedManager
import ac.aux.compose.nostr.Relays
import ac.aux.compose.repository.NostrRepository
import ac.aux.compose.ui.view.state.NavigationUIState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.utils.text
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlin.time.Instant

class NavigationViewModel(
    initialNavigationUIState: NavigationUIState,
    val nostrRepository: NostrRepository,
    val scope: CoroutineScope,
): ViewModel() {

    companion object {
        private const val TAG = "NavigationViewModel"

        fun factory(
            initialNavigationUIState: NavigationUIState,
            nostrRepository: NostrRepository,
            scope: CoroutineScope,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                NavigationViewModel(
                    initialNavigationUIState = initialNavigationUIState,
                    nostrRepository = nostrRepository,
                    scope = scope
                )
            }
        }
    }

    private val logger = Logger.withTag(TAG)
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


    private val _navigationUIState = MutableStateFlow(
        initialNavigationUIState
    )
    val navigationUIState = _navigationUIState.asStateFlow()

    init {
        observeUnsignedNostrEvents()
        observePendingBroadcastNostrEventRequests()
        observeProfile()
    }


    private fun observeUnsignedNostrEvents() {
        val tempSigner = NostrSignerSync(
            SeedManager.activeKeyPair()
        )
        logger.i { "observeUnsignedNostrEvents" }
        scope.launch(Dispatchers.IO) {
            nostrRepository.observeUnsignedNostrEvents(
                publicKey = SeedManager.activePublicKey().toHexKey()
            ).collect { unsignedNostrEventOrNull ->
                unsignedNostrEventOrNull?.let { unsignedNostrEvent ->
                    logger.d("Unsigned")
                    val event = tempSigner.signNormal<Event>(
                        createdAt = unsignedNostrEvent.createdAt.toEpochMilliseconds(),
                        kind = unsignedNostrEvent.kind,
                        tags = unsignedNostrEvent.tags,
                        content = unsignedNostrEvent.content
                    )


                    nostrRepository.publishNostrEvent(
                        unsignedNostrEvent,
                        NostrEvent(
                            id = event.id,
                            pubKey = event.pubKey,
                            kind = event.kind,
                            tags = event.tags,
                            content = event.content,
                            createdAt = Instant.fromEpochMilliseconds(event.createdAt),
                            sig = event.sig,
                            unsignedNostrEventId = unsignedNostrEvent.id
                        ),
                        relayURLs = Relays.eventPublishRelaySet.map { normalizedRelayUrl -> normalizedRelayUrl.url }
                    )
                }

            }
        }
    }

    private fun observePendingBroadcastNostrEventRequests() {
        logger.i { "observePendingBroadcastNostrEventRequests" }

        scope.launch(Dispatchers.IO) {

            nostrRepository.observePendingBroadcastNostrEventRequests().collect { localBroadcastNostrEventRequests ->
                localBroadcastNostrEventRequests.forEach { localBroadcastNostrEventRequest ->
                    val event: Event = localBroadcastNostrEventRequest.nostrEvent.let { nostrEvent ->
                        Event(
                            id = nostrEvent.id,
                            kind = nostrEvent.kind,
                            pubKey = nostrEvent.pubKey,
                            content = nostrEvent.content,
                            tags = nostrEvent.tags,
                            sig = nostrEvent.sig,
                            createdAt = nostrEvent.createdAt.toEpochMilliseconds()
                        )
                    }
                    val eventJson = event.toJson()
                    logger.d("Broadcasting (${localBroadcastNostrEventRequest.broadcastNostrEventRequest.relayURL}): $eventJson")

                    scope.launch {

                        val webSocketSession = httpClient.webSocketSession(
                            urlString = localBroadcastNostrEventRequest.broadcastNostrEventRequest.relayURL
                        )

                        broadcastingJobs[localBroadcastNostrEventRequest.broadcastNostrEventRequest.relayURL] = scope.launch {
                            webSocketSession.send(
                                frame = Frame.Text("[\"EVENT\",${eventJson}]")
                            )

                            nostrRepository.broadcastProcessed(
                                localBroadcastNostrEventRequest.broadcastNostrEventRequest
                            )

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
                                                            nostrRepository.saveBroadcastReceipt(
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
//                        nostrClient.send(
//                            event = event,
//                            relayList = setOf(
//                                RelayUrlNormalizer.normalize(localBroadcastNostrEventRequest.broadcastNostrEventRequest.relayURL)
//                            )
//                        )
                    }
                }

            }
        }
    }
    private fun observeProfile() {
        logger.i("observeProfile")
        scope.launch(Dispatchers.IO) {
            delay(2_100) // Looking busy...

            logger.i("Navigation UI State is Landing")
            val publicKey = SeedManager.activePublicKey().toHexKey()
            logger.i("Observing: $publicKey")
            nostrRepository.observeProfile(
                publicKey = publicKey
            ).collect { localProfile ->
                logger.i("Local Profile: $localProfile")
                _navigationUIState.getAndUpdate {
                    if (localProfile == null) {
                        logger.i("We need to be on the landing screen so user creates account")
                        NavigationUIState.Landing
                    } else if (localProfile.unsignedNostrEvent == null) {
                        logger.i("We need to be on the landing screen so user creates account")
                        NavigationUIState.Landing
                    } else if (localProfile.broadcastNostrEventReceipt != null) {
                        logger.i("We have a broadcast receipt: ${localProfile.profile}")
                        NavigationUIState.ProfileLoaded(
                            broadcastNostrEventReceipt = localProfile.broadcastNostrEventReceipt
                        )
                    } else if (localProfile.broadcastNostrEventRequest != null) {
                        logger.i("We have a broadcast request: ${localProfile.broadcastNostrEventRequest}")
                        NavigationUIState.UnannouncedProfile(
                            broadcastNostrEventRequest = localProfile.broadcastNostrEventRequest
                        )
                    }  else if (localProfile.profile != null) {
                        logger.i("We have a profile that needs to queued for broadcast: ${localProfile.profile}")
                        NavigationUIState.UnqueuedProfile(
                            profile = localProfile.profile
                        )
                    } else if (localProfile.nostrEvent != null) {
                        logger.i("Nostr event with the profile needs to be indexed: ${localProfile.nostrEvent}")
                        NavigationUIState.UnindexedProfile(
                            nostrEvent = localProfile.nostrEvent
                        )
                    } else if (localProfile.unsignedNostrEvent != null && localProfile.unsignedNostrEvent.signedAt != null) {
                        logger.i("We should be syncing the profile: ${localProfile.unsignedNostrEvent}")
                        NavigationUIState.UnsyncedProfile(
                            unsignedNostrEvent = localProfile.unsignedNostrEvent
                        )
                    } else {
                        logger.i("We have an unsigned profile: ${localProfile.unsignedNostrEvent}")
                        NavigationUIState.UnsignedProfile(
                            unsignedNostrEvent = localProfile.unsignedNostrEvent
                        )
                    }
                }
            }
        }
    }

}