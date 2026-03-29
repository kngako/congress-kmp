package ac.aux.compose.ui.view.model

import ac.aux.compose.database.model.NostrEvent
import ac.aux.compose.managers.SeedManager
import ac.aux.compose.network.NostrEventBroadcaster
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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.collections.set
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

    private val syncingJobs = mutableMapOf<String, Job>()

    val httpClient = HttpClient() {
        install(WebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(Json {
                isLenient = true
                ignoreUnknownKeys = true
            })
            pingIntervalMillis = 20_000
        }
    }

    val nostrEventBroadcaster = NostrEventBroadcaster(
        scope = scope
    )

    private val _navigationUIState = MutableStateFlow(
        initialNavigationUIState
    )
    val navigationUIState = _navigationUIState.asStateFlow()

    init {
        observeUnsignedNostrEvents()
        observePendingBroadcastNostrEventRequests()
        observeProfile()
        observeSyncNostrEventRequests()
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

    private fun observeSyncNostrEventRequests() {
        logger.i { "observeSyncNostrEventRequests" }

        scope.launch(Dispatchers.IO) {
            nostrRepository.observePendingSynchronizeNostrEventRequests().collect { synchronizeNostrEventRequestOrNull ->
                synchronizeNostrEventRequestOrNull?.let { synchronizeNostrEventRequest ->
                    val filterContent = mutableMapOf<String, JsonElement>()

                    synchronizeNostrEventRequest.eventIds?.let { eventIds ->
                        filterContent.put("ids", JsonPrimitive(eventIds))
                    }
                    synchronizeNostrEventRequest.authorPublicKeys?.let { authorPublicKeys ->
                        filterContent.put("authors", JsonPrimitive(authorPublicKeys))
                    }
                    synchronizeNostrEventRequest.kinds?.let { kinds ->
                        filterContent.put("kinds", JsonPrimitive(kinds))
                    }
                    synchronizeNostrEventRequest.since?.let { since ->
                        filterContent.put("since", JsonPrimitive(since.toEpochMilliseconds()))
                    }
                    synchronizeNostrEventRequest.until?.let { until ->
                        filterContent.put("until", JsonPrimitive(until.toEpochMilliseconds()))
                    }
                    synchronizeNostrEventRequest.limit?.let { limit ->
                        filterContent.put("limit", JsonPrimitive(limit))
                    }

                    val filter = JsonObject(
                        filterContent
                    )
                    logger.d("Syncing: $filter")
                    scope.launch {
                        val webSocketSession = httpClient.webSocketSession(
                            urlString = synchronizeNostrEventRequest.relayURL
                        )

                        syncingJobs[synchronizeNostrEventRequest.relayURL] = scope.launch {
                            webSocketSession.send(
                                frame = Frame.Text("[\"REQ\",${synchronizeNostrEventRequest.id},${filter}]")
                            )

                            nostrRepository.synchronizeNostrEventRequestProcessed(synchronizeNostrEventRequest)

                            webSocketSession.incoming.consumeAsFlow().collect { frame ->
                                when(frame) {
                                    is Frame.Text -> {
                                        frame.readText().let { text ->
                                            try {
                                                logger.d("WebSocket Read Text: $text")
                                                val result = Json.decodeFromString<JsonArray>(text)

                                                if (result.firstOrNull()?.text == "EVENT") {
                                                    result.getOrNull(1)?.text?.let { subscriptionId ->
                                                        if (subscriptionId == synchronizeNostrEventRequest.id) {

                                                            result.getOrNull(2)?.text?.let { jsonText ->
                                                                val event = Event.fromJson(
                                                                    jsonText
                                                                )

                                                                val nostrEvent = NostrEvent(
                                                                    id = event.id,
                                                                    pubKey = event.pubKey,
                                                                    kind = event.kind,
                                                                    content = event.content,
                                                                    tags = event.tags,
                                                                    createdAt = Instant.fromEpochMilliseconds(event.createdAt),
                                                                    sig = event.sig
                                                                )

                                                                nostrRepository.saveNostrEvent(
                                                                    nostrEvent = nostrEvent,
                                                                    synchronizeNostrEventRequest,
                                                                    synchronizationRelayURLs = Relays.eventPublishRelaySet.map { normalizedRelayUrl -> normalizedRelayUrl.url }
                                                                )

                                                            }
                                                        }
                                                    }
                                                } else {
                                                    logger.d("Unsupported: $text")
                                                }
                                            } catch (e: Throwable) {
                                                logger.e("Error processing receipt: $text", e)
                                            }
                                        }
                                        // Syncing doesn't necessary have to close after result..
                                        webSocketSession.close()
                                        syncingJobs.remove(synchronizeNostrEventRequest.relayURL)?.cancel()
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

                        syncingJobs[synchronizeNostrEventRequest.relayURL]?.join()
                    }
                }
            }
        }
    }

    private fun observePendingBroadcastNostrEventRequests() {
        logger.i { "observePendingBroadcastNostrEventRequests" }

        scope.launch(Dispatchers.IO) {
            nostrRepository.observePendingBroadcastNostrEventRequests().collect { localBroadcastNostrEventRequests ->
                localBroadcastNostrEventRequests.forEach { localBroadcastNostrEventRequest ->
                    nostrEventBroadcaster.broadcastEvent(
                        localBroadcastNostrEventRequest = localBroadcastNostrEventRequest,
                        onBroadcastRequestProcessed = { broadcastNostrEventRequest ->
                            scope.launch(Dispatchers.IO) {
                                nostrRepository.broadcastProcessed(
                                    broadcastNostrEventRequest
                                )
                            }
                        },
                        onBroadcastReceipt = { broadcastNostrEventReceipt ->
                            scope.launch(Dispatchers.IO) {
                                nostrRepository.saveBroadcastReceipt(
                                    broadcastNostrEventReceipt
                                )
                            }
                        }
                    )
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
                    } else if (localProfile.unsignedNostrEvent.signedAt != null && localProfile.synchronizeNostrEventRequests.isEmpty()) {
                        logger.i("We should request to sync profile: ${localProfile.unsignedNostrEvent}")
                        NavigationUIState.UnqueuedProfileSynchronization(
                            unsignedNostrEvent = localProfile.unsignedNostrEvent
                        )
                    }  else if (localProfile.unsignedNostrEvent.signedAt != null) {
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