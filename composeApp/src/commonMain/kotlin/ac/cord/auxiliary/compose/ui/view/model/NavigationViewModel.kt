package ac.cord.auxiliary.compose.ui.view.model

import ac.cord.auxiliary.compose.database.model.BroadcastNostrEventRequest
import ac.cord.auxiliary.compose.database.model.NostrEvent
import ac.cord.auxiliary.compose.database.model.SynchronizeNostrEventRequest
import ac.cord.auxiliary.compose.database.model.types.SynchronizationFilter
import ac.cord.auxiliary.compose.managers.SeedManager
import ac.cord.auxiliary.compose.network.dto.RelayDTO
import ac.cord.auxiliary.compose.network.relays.RelayPool.Companion.PUBLISH_TIMEOUT
import ac.cord.auxiliary.compose.network.relays.RelaysSocketManager
import ac.cord.auxiliary.compose.network.sockets.NostrIncomingMessage
import ac.cord.auxiliary.compose.network.sockets.NostrSocketClientFactory
import ac.cord.auxiliary.compose.nostr.Relays
import ac.cord.auxiliary.compose.repository.CachingImportRepository
import ac.cord.auxiliary.compose.repository.NostrRepository
import ac.cord.auxiliary.compose.repository.RelayRepository
import ac.cord.auxiliary.compose.ui.view.state.NavigationUIState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.touchlab.kermit.Logger
import com.vitorpamplona.negentropy.Negentropy
import com.vitorpamplona.negentropy.storage.StorageVector
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CloseCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip51Lists.encryption.PrivateTagsInContent
import com.vitorpamplona.quartz.nip77Negentropy.NegCloseCmd
import com.vitorpamplona.quartz.nip77Negentropy.NegOpenCmd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

class NavigationViewModel(
    initialNavigationUIState: NavigationUIState,
    val nostrRepository: NostrRepository,
    val relayRepository: RelayRepository,
    val scope: CoroutineScope,
): ViewModel() {

    val relaysSocketManager = RelaysSocketManager(
        nostrSocketClientFactory = NostrSocketClientFactory,
        cachingImportRepository = CachingImportRepository.NO_OP_CACHING_IMPORT_REPOSITORY,
        relayRepository = relayRepository,
    )

    companion object {
        private const val TAG = "NavigationViewModel"

        private val mutex = Mutex()

        fun factory(
            initialNavigationUIState: NavigationUIState,
            nostrRepository: NostrRepository,
            relayRepository: RelayRepository,
            scope: CoroutineScope,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                NavigationViewModel(
                    initialNavigationUIState = initialNavigationUIState,
                    nostrRepository = nostrRepository,
                    relayRepository = relayRepository,
                    scope = scope
                )
            }
        }
    }

    private val logger = Logger.withTag(TAG)

    private val _navigationUIState = MutableStateFlow(
        initialNavigationUIState
    )
    val navigationUIState = _navigationUIState.asStateFlow()

    init {
        observeUnsignedNostrEvents()
        observePendingBroadcastNostrEventRequests()
        observeProfile()
        observePendingSyncNostrEventRequests()
        observePendingNegentropySynchronizeRequests()
    }


    private fun observeUnsignedNostrEvents() {
        val tempSigner = NostrSignerSync(
            SeedManager.activeKeyPair()
        )
        logger.i { "observeUnsignedNostrEvents" }
        scope.launch(Dispatchers.IO) {
            nostrRepository.observeUnsignedNostrEvents(
                publicKey = SeedManager.activePublicKey().toHexKey()
            ).distinctUntilChanged().collect { unsignedNostrEventOrNull ->
                unsignedNostrEventOrNull?.let { unsignedNostrEvent ->
                    logger.d("Unsigned")
                    val event = tempSigner.signNormal<Event>(
                        createdAt = unsignedNostrEvent.createdAt.epochSeconds,
                        kind = unsignedNostrEvent.kind,
                        tags = unsignedNostrEvent.tags,
                        content = unsignedNostrEvent.privateTags?.let { PrivateTagsInContent.encryptNip44(it, tempSigner) } ?: unsignedNostrEvent.content
                    )

                    nostrRepository.publishNostrEvent(
                        unsignedNostrEvent,
                        NostrEvent(
                            id = event.id,
                            pubKey = event.pubKey,
                            kind = event.kind,
                            tags = event.tags,
                            content = event.content,
                            createdAt = Instant.fromEpochSeconds(event.createdAt),
                            sig = event.sig,
                            unsignedNostrEventId = unsignedNostrEvent.id
                        ),
                        relayURLs = Relays.eventPublishRelaySet.map { normalizedRelayUrl -> normalizedRelayUrl.url }
                    )
                }

            }
        }
    }

    private fun observePendingSyncNostrEventRequests() {
        logger.i { "observePendingSyncNostrEventRequests" }

        scope.launch(Dispatchers.IO) {
            nostrRepository.observePendingSynchronizeNostrEventRequests().distinctUntilChanged().collect { synchronizeNostrEventRequestOrNull ->
                synchronizeNostrEventRequestOrNull?.let { synchronizeNostrEventRequest ->
                    logger.i("synchronizeNostrEventRequest: $synchronizeNostrEventRequest")
                    val reqCommand = ReqCmd(
                        subId = synchronizeNostrEventRequest.id,
                        filters = synchronizeNostrEventRequest.synchronizationFilters.map { synchronizationFilter ->
                            Filter(
                                ids = synchronizationFilter.ids?.toList(),
                                authors = synchronizationFilter.authors?.toList(),
                                kinds = synchronizationFilter.kinds?.toList(),
                                tags = synchronizationFilter.tags,
                                tagsAll = synchronizationFilter.tagsAll,
                                since = synchronizationFilter.since?.epochSeconds,
                                until = synchronizationFilter.until?.epochSeconds,
                                limit = synchronizationFilter.limit,
                                search = synchronizationFilter.search
                            )
                        }
                    )

                    nostrRepository.synchronizeNostrEventRequestProcessed(synchronizeNostrEventRequest)

                    scope.launch(Dispatchers.IO) {
                        try {
                            relaysSocketManager.query(
                                reqCommand,
                                synchronizeNostrEventRequest.relayURL
                            ).collect { nostrIncomingMessage ->
                                when (nostrIncomingMessage) {
                                    is NostrIncomingMessage.EventMessage -> {
                                        scope.launch(Dispatchers.IO) {
                                            logger.d("Import message: $nostrIncomingMessage")
                                            nostrIncomingMessage.nostrEvent?.let {
                                                nostrRepository.saveNostrEvent(
                                                    nostrEvent = it,
                                                    synchronizeNostrEventRequest,
                                                    synchronizationRelayURLs = listOf(synchronizeNostrEventRequest.relayURL) // TODO: + Relays.eventPublishRelaySet.map { normalizedRelayUrl -> normalizedRelayUrl.url }
                                                )
                                            }
                                        }
                                    }
                                    is NostrIncomingMessage.EventsMessage -> {
                                        logger.d("Import messages: $nostrIncomingMessage")

                                        nostrIncomingMessage.nostrEvents.forEach { nostrEvent ->
                                            nostrRepository.saveNostrEvent(
                                                nostrEvent = nostrEvent,
                                                synchronizeNostrEventRequest,
                                                synchronizationRelayURLs = listOf(synchronizeNostrEventRequest.relayURL) // TODO: + Relays.eventPublishRelaySet.map { normalizedRelayUrl -> normalizedRelayUrl.url }
                                            )
                                        }
                                    }
                                    is NostrIncomingMessage.EoseMessage -> {
                                        logger.d("Sync request has been successfully processed (${synchronizeNostrEventRequest.relayURL}): $nostrIncomingMessage")
                                        val closeCommand = CloseCmd(
                                            subId = synchronizeNostrEventRequest.id,
                                        )

                                        relaysSocketManager.closeQuery(
                                            closeCommand,
                                            synchronizeNostrEventRequest.relayURL
                                        )
                                    }
                                    else -> {
                                        logger.d("Unhandled message ${synchronizeNostrEventRequest.relayURL}: $nostrIncomingMessage")
                                    }
                                }
                            }

                        } catch (e: Throwable) {
                            logger.e("Failed to sync", e)
                        }
                    }
                }
            }
        }
    }

    private fun observePendingNegentropySynchronizeRequests() {
        logger.i { "observePendingNegentropySynchronizeRequests" }

        scope.launch(Dispatchers.IO) {
            nostrRepository.observePendingNegentropySynchronizeRequests().distinctUntilChanged().collect { negentropySynchronizeRequestOrNull ->
                negentropySynchronizeRequestOrNull?.let { negentropySynchronizeRequest ->
                    mutex.withLock {
                        logger.i("negentropySynchronizeRequest: $negentropySynchronizeRequest")

                        val events = nostrRepository.getNostrFeedIds(
                            arrayOf(
                                negentropySynchronizeRequest.synchronizationFilter
                            ),
                            applyLimits = false
                        )
                        logger.d("Events: ${events.size}")
                        val storage = StorageVector().apply {
                            events.forEach { event ->
                                insert(
                                    event.createdAt.toEpochMilliseconds(),
                                    event.id
                                )
                            }

                            seal()
                        }
                        val negentropy = Negentropy(
                            storage,
                        )

                        val negOpenCmd = NegOpenCmd(
                            subId = negentropySynchronizeRequest.uuid,
                            filter = Filter(
                                ids = negentropySynchronizeRequest.synchronizationFilter.ids?.toList(),
                                authors = negentropySynchronizeRequest.synchronizationFilter.authors?.toList(),
                                kinds = negentropySynchronizeRequest.synchronizationFilter.kinds?.toList(),
                                tags = negentropySynchronizeRequest.synchronizationFilter.tags,
                                tagsAll = negentropySynchronizeRequest.synchronizationFilter.tagsAll,
                                since = negentropySynchronizeRequest.synchronizationFilter.since?.epochSeconds,
                                until = negentropySynchronizeRequest.synchronizationFilter.until?.epochSeconds,
                                limit = null, // We don't do limits when performing negentropy...
                                search = negentropySynchronizeRequest.synchronizationFilter.search
                            ),
                            initialMessage = negentropy.initiate().toHexString()
                        )
                        logger.d("negOpenCmd: ${negOpenCmd.initialMessage}")

                        nostrRepository.negentropySynchronizeRequestProcessed(negentropySynchronizeRequest)

                        scope.launch(Dispatchers.IO) {
                            try {
                                val negCloseCmd = NegCloseCmd(
                                    subId = negentropySynchronizeRequest.uuid,
                                )

                                relaysSocketManager.negentropySync(
                                    negOpenCmd,
                                    negentropySynchronizeRequest.relayURL
                                ).collect { nostrIncomingMessage ->
                                    when (nostrIncomingMessage) {
                                        is NostrIncomingMessage.EventMessage -> {
                                            scope.launch(Dispatchers.IO) {
                                                logger.d("Import message: $nostrIncomingMessage")
                                                nostrIncomingMessage.nostrEvent?.let {
                                                    nostrRepository.saveNostrEvent(
                                                        nostrEvent = it,
                                                        negentropySynchronizeRequest,
                                                        synchronizationRelayURLs = listOf(negentropySynchronizeRequest.relayURL) // TODO: + Relays.eventPublishRelaySet.map { normalizedRelayUrl -> normalizedRelayUrl.url }
                                                    )
                                                }
                                            }
                                        }
                                        is NostrIncomingMessage.EventsMessage -> {
                                            logger.d("Import messages: $nostrIncomingMessage")

                                            nostrIncomingMessage.nostrEvents.forEach { nostrEvent ->
                                                nostrRepository.saveNostrEvent(
                                                    nostrEvent = nostrEvent,
                                                    negentropySynchronizeRequest,
                                                    synchronizationRelayURLs = listOf(negentropySynchronizeRequest.relayURL) // TODO: + Relays.eventPublishRelaySet.map { normalizedRelayUrl -> normalizedRelayUrl.url }
                                                )
                                            }
                                        }
                                        is NostrIncomingMessage.EoseMessage -> {
                                            logger.d("Sync request has been successfully processed (${negentropySynchronizeRequest.relayURL}): $nostrIncomingMessage")

                                            relaysSocketManager.closeNegentropySync(
                                                negCloseCmd,
                                                negentropySynchronizeRequest.relayURL
                                            )

                                            return@collect
                                        }
                                        is NostrIncomingMessage.NegentropyError -> {
                                            logger.d("Negentropy Error (need to synchronize like normal): ${nostrIncomingMessage.negentropyReason}")

                                            // TODO: Don't schedule a sync when on mobile internet.
                                            val since = negentropySynchronizeRequest.synchronizationFilter.since // TODO: Update since -> until based on what we have in the local db...
                                            val until = negentropySynchronizeRequest.synchronizationFilter.until

                                            val synchronizationFilter = negentropySynchronizeRequest.synchronizationFilter.copy(
                                                since = since,
                                                until = until
                                            )

                                            nostrRepository.queueSynchronizeNostrEvent(
                                                listOf(
                                                    SynchronizeNostrEventRequest(
                                                        purpose = negentropySynchronizeRequest.purpose,
                                                        synchronizationFilters = arrayOf(
                                                            synchronizationFilter
                                                        ),
                                                        relayURL = negentropySynchronizeRequest.relayURL,
                                                        level = negentropySynchronizeRequest.level,
                                                    )
                                                )
                                            )
                                            return@collect
                                        }
                                        is NostrIncomingMessage.NegentropyMessage -> {
                                            logger.d("NegentropyMessage: ${nostrIncomingMessage.negentropyMessage}")

                                            val result = negentropy.reconcile(
                                                nostrIncomingMessage.negentropyMessage.hexToByteArray()
                                            )
                                            logger.d("NeedIds: ${result.needIds.map { it.toHexString() }}")
                                            logger.d("SendIds: ${result.sendIds.map { it.toHexString() }}")
                                            logger.d("EventsIds: ${events.map { it.id }}")
                                            logger.d("Timestamp: ${events.map { it.createdAt.epochSeconds }}")

                                            if (result.needIds.isNotEmpty()) {
                                                // Schedule a sync from this relay...
                                                nostrRepository.queueSynchronizeNostrEvent(
                                                    listOf(
                                                        SynchronizeNostrEventRequest(
                                                            purpose = negentropySynchronizeRequest.purpose,
                                                            synchronizationFilters = arrayOf(
                                                                SynchronizationFilter(
                                                                    ids = result.needIds.map { it.toHexString() }.toTypedArray()
                                                                )
                                                            ),
                                                            relayURL = negentropySynchronizeRequest.relayURL,
                                                            level = negentropySynchronizeRequest.level,
                                                        )
                                                    )
                                                )
                                            }
                                            val broadcastNostrEventRequests = result.sendIds.map { nostrEventId ->
                                                BroadcastNostrEventRequest(
                                                    nostrEventId = nostrEventId.toHexString(),
                                                    relayURL = negentropySynchronizeRequest.relayURL
                                                )
                                            }
                                            nostrRepository.scheduleBroadcastNostrEventRequests(
                                                broadcastNostrEventRequests
                                            )

                                            relaysSocketManager.closeNegentropySync(
                                                negCloseCmd,
                                                negentropySynchronizeRequest.relayURL
                                            )

                                            return@collect
                                        }
                                        else -> {
                                            logger.d("Unhandled message ${negentropySynchronizeRequest.relayURL}: $nostrIncomingMessage")
                                        }
                                    }
                                }
                                logger.d("Queried Sync")
                            } catch (e: Throwable) {
                                logger.e("Failed to sync", e)
                            }
                        }

                        logger.i("After launch")
                    }
                    logger.i("Lock released")
                }
            }
        }
    }

    @OptIn(FlowPreview::class)
    private fun observePendingBroadcastNostrEventRequests() {
        logger.i { "observePendingBroadcastNostrEventRequests" }

        scope.launch(Dispatchers.IO) {
            nostrRepository.observePendingBroadcastNostrEventRequests().distinctUntilChanged().collect { localBroadcastNostrEventRequests ->
                localBroadcastNostrEventRequests.forEach { localBroadcastNostrEventRequest ->

                    nostrRepository.broadcastProcessed(
                        localBroadcastNostrEventRequest.broadcastNostrEventRequest
                    )

                    if (localBroadcastNostrEventRequest.nostrEvent.broadcastedAt == null) {
                        scope.launch(Dispatchers.IO) {
                            relaysSocketManager.publishEvent(
                                localBroadcastNostrEventRequest.nostrEvent
                            ).timeout(PUBLISH_TIMEOUT.milliseconds).catch {
                                // Timeout...
                            }.collect { nostrPublishResult ->
                                if (nostrPublishResult.error != null) {
                                    logger.e("Error publishing note: $nostrPublishResult")
                                    nostrRepository.broadcastProcessed(
                                        localBroadcastNostrEventRequest.broadcastNostrEventRequest,
                                        "failed"
                                    )
                                } else {
                                    logger.d("Nostr Publish Result: $nostrPublishResult")
                                    nostrRepository.broadcastProcessed(
                                        localBroadcastNostrEventRequest.broadcastNostrEventRequest,
                                        "published"
                                    )
                                }
                            }
                        }
                    }

                    scope.launch(Dispatchers.IO) {
                        // Broadcast to the intended relay...
                        relaysSocketManager.publishEvent(
                            localBroadcastNostrEventRequest.nostrEvent,
                            setOf(
                                RelayDTO(
                                    localBroadcastNostrEventRequest.broadcastNostrEventRequest.relayURL,
                                    write = true,
                                    read = true
                                )
                            )
                        ).timeout(PUBLISH_TIMEOUT.milliseconds).catch {
                            // Timeout...
                        }.collect { nostrPublishResult ->
                            if (nostrPublishResult.error != null) {
                                logger.e("Error publishing note: $nostrPublishResult")
                                nostrRepository.broadcastProcessed(
                                    localBroadcastNostrEventRequest.broadcastNostrEventRequest,
                                    "failed"
                                )
                            } else {
                                logger.d("Nostr Publish Result: $nostrPublishResult")
                                nostrRepository.broadcastProcessed(
                                    localBroadcastNostrEventRequest.broadcastNostrEventRequest,
                                    "published"
                                )
                            }
                        }
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
            ).distinctUntilChanged().collect { localProfile ->
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
                            publicKey = localProfile.unsignedNostrEvent.pubKey
                        )
                    } else if (localProfile.unsignedNostrEvent.signedAt != null && localProfile.profile != null) {
                        logger.i("We have successfully synced a profile: ${localProfile.profile}")
                        NavigationUIState.ProfileLoaded(
                            publicKey = localProfile.unsignedNostrEvent.pubKey
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
                    }  else if (localProfile.unsignedNostrEvent.signedAt != null && localProfile.synchronizeNostrEventRequests.isEmpty()) {
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