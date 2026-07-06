package at.torch.compose.ui.view.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import at.torch.compose.database.model.BroadcastNostrEventRequest
import at.torch.compose.database.model.SynchronizeNostrEventRequest
import at.torch.compose.database.model.types.SynchronizationFilter
import at.torch.compose.network.dto.toRelayDTO
import at.torch.compose.network.relays.RelayPool
import at.torch.compose.network.relays.RelaysSocketManager
import at.torch.compose.network.sockets.NostrIncomingMessage
import at.torch.compose.network.sockets.NostrSocketClientFactory
import at.torch.compose.repository.CachingImportRepository
import at.torch.compose.repository.ChatRepository
import at.torch.compose.repository.NostrRepository
import at.torch.compose.repository.RelayRepository
import co.touchlab.kermit.Logger
import com.vitorpamplona.negentropy.Negentropy
import com.vitorpamplona.negentropy.storage.StorageVector
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CloseCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip77Negentropy.NegCloseCmd
import com.vitorpamplona.quartz.nip77Negentropy.NegOpenCmd
import fr.acinq.phoenix.data.ActiveWallet
import fr.acinq.phoenix.managers.nostrPrivateKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.milliseconds

class SynchronizationViewModel(
    val activeWalletStateFlow: StateFlow<ActiveWallet?>,
    val nostrRepository: NostrRepository,
    val chatRepository: ChatRepository,
    val relayRepository: RelayRepository,
    val scope: CoroutineScope,
): ViewModel() {

    val relaysSocketManager = RelaysSocketManager(
        activeWalletStateFlow = activeWalletStateFlow,
        nostrSocketClientFactory = NostrSocketClientFactory,
        cachingImportRepository = CachingImportRepository.NO_OP_CACHING_IMPORT_REPOSITORY,
        relayRepository = relayRepository,
    )

    companion object {
        private const val TAG = "SynchronizationViewModel"

        private val mutex = Mutex()

        fun factory(
            activeWalletStateFlow: StateFlow<ActiveWallet?>,
            nostrRepository: NostrRepository,
            chatRepository: ChatRepository,
            relayRepository: RelayRepository,
            scope: CoroutineScope,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SynchronizationViewModel(
                    activeWalletStateFlow = activeWalletStateFlow,
                    nostrRepository = nostrRepository,
                    chatRepository = chatRepository,
                    relayRepository = relayRepository,
                    scope = scope
                )
            }
        }
    }

    private val logger = Logger.withTag(TAG)

    init {
        scope.launch {
            activeWalletStateFlow.collectLatest { activeWallet ->
                logger.d("Synchronization ActiveWallet: $activeWallet")
                activeWallet?.business?.walletManager?.keyManager?.value?.nostrPrivateKey()?.let { nostrPrivateKey ->
                    val keyPair = KeyPair(
                        privKey = nostrPrivateKey.value.toByteArray()
                    )

                    observePendingBroadcastNostrEventRequests(keyPair)
                    observePendingSyncNostrEventRequests(keyPair)
                    observePendingNegentropySynchronizeRequests(keyPair)
                }
            }
        }

    }

    private fun observePendingSyncNostrEventRequests(
        keyPair: KeyPair
    ) {
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
                                                    synchronizationRelayURLs = listOf(synchronizeNostrEventRequest.relayURL),
                                                    activeKeyPair = keyPair
                                                    // TODO: + Relays.eventPublishRelaySet.map { normalizedRelayUrl -> normalizedRelayUrl.url }
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
                                                synchronizationRelayURLs = listOf(synchronizeNostrEventRequest.relayURL),
                                                activeKeyPair = keyPair
                                                // TODO: + Relays.eventPublishRelaySet.map { normalizedRelayUrl -> normalizedRelayUrl.url }
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

    private fun observePendingNegentropySynchronizeRequests(
        keyPair: KeyPair
    ) {
        logger.i { "observePendingNegentropySynchronizeRequests" }

        scope.launch(Dispatchers.IO) {
            nostrRepository.observePendingNegentropySynchronizeRequests().distinctUntilChanged().collect { negentropySynchronizeRequestOrNull ->
                negentropySynchronizeRequestOrNull?.let { negentropySynchronizeRequest ->
                    mutex.withLock {
                        logger.i("negentropySynchronizeRequest: $negentropySynchronizeRequest")

                        val events = nostrRepository.getNegentropicNostrFeedIds(
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
                                                        synchronizationRelayURLs = listOf(negentropySynchronizeRequest.relayURL),
                                                        activeKeyPair = keyPair
                                                        // TODO: + Relays.eventPublishRelaySet.map { normalizedRelayUrl -> normalizedRelayUrl.url }
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
                                                    synchronizationRelayURLs = listOf(negentropySynchronizeRequest.relayURL),
                                                    activeKeyPair = keyPair
                                                    // TODO: + Relays.eventPublishRelaySet.map { normalizedRelayUrl -> normalizedRelayUrl.url }
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
                                            if (negentropySynchronizeRequest.purpose == "mlsMessages") {
                                                nostrRepository.queueSynchronizeNostrEvent(
                                                    listOf(
                                                        negentropySynchronizeRequest.toSynchronizeNostrEventRequest()
                                                    )
                                                )
                                            }
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
                                                                    ids = result.needIds.map { it.toHexString() }
                                                                        .toTypedArray()
                                                                )
                                                            ),
                                                            relayURL = negentropySynchronizeRequest.relayURL,
                                                            level = negentropySynchronizeRequest.level,
                                                        )
                                                    )
                                                )
                                            }
                                            val eventIds = events.map { it.id }
                                            val broadcastNostrEventRequests = result.sendIds.filter { it.toHexString() in eventIds }.map { sendId ->
                                                BroadcastNostrEventRequest(
                                                    nostrEventId = sendId.toHexString(),
                                                    relayURL = negentropySynchronizeRequest.relayURL
                                                )
                                            }
                                            logger.d("broadcastNostrEventRequests: $broadcastNostrEventRequests")
                                            // TODO: Schedule broadcastNostrEventRequests
//                                            nostrRepository.scheduleBroadcastNostrEventRequests(
//                                                broadcastNostrEventRequests
//                                            )

                                            relaysSocketManager.closeNegentropySync(
                                                negCloseCmd,
                                                negentropySynchronizeRequest.relayURL
                                            )

                                            return@collect
                                        }
                                        is NostrIncomingMessage.NoticeMessage -> {
                                            logger.d("Notice message ${negentropySynchronizeRequest.relayURL}: $nostrIncomingMessage")
                                            if (nostrIncomingMessage.subscriptionId?.contains("negentropy disabled") == true) {
                                                if (negentropySynchronizeRequest.purpose == "mlsMessages") {
                                                    nostrRepository.queueSynchronizeNostrEvent(
                                                        listOf(
                                                            negentropySynchronizeRequest.toSynchronizeNostrEventRequest()
                                                        )
                                                    )
                                                }
                                            }
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
    private fun observePendingBroadcastNostrEventRequests(
        keyPair: KeyPair
    ) {
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
                            ).timeout(RelayPool.PUBLISH_TIMEOUT.milliseconds).catch {
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
                                        "published" // TODO: Might want to pass the nostrPublishResult.result
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
                                localBroadcastNostrEventRequest.broadcastNostrEventRequest.relayURL.toRelayDTO()
                            )
                        ).timeout(RelayPool.PUBLISH_TIMEOUT.milliseconds).catch {
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
}