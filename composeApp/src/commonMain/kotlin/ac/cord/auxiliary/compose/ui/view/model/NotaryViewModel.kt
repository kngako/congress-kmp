package ac.cord.auxiliary.compose.ui.view.model

import ac.cord.auxiliary.compose.database.model.BroadcastNostrEventRequest
import ac.cord.auxiliary.compose.database.model.GiftWrapSeal
import ac.cord.auxiliary.compose.database.model.NostrEvent
import ac.cord.auxiliary.compose.database.model.SynchronizeNostrEventRequest
import ac.cord.auxiliary.compose.database.model.types.SynchronizationFilter
import ac.cord.auxiliary.compose.managers.SeedManager
import ac.cord.auxiliary.compose.network.dto.toRelayDTO
import ac.cord.auxiliary.compose.network.relays.RelayPool.Companion.PUBLISH_TIMEOUT
import ac.cord.auxiliary.compose.network.relays.RelaysSocketManager
import ac.cord.auxiliary.compose.network.sockets.NostrIncomingMessage
import ac.cord.auxiliary.compose.network.sockets.NostrSocketClientFactory
import ac.cord.auxiliary.compose.nostr.Relays
import ac.cord.auxiliary.compose.repository.CachingImportRepository
import ac.cord.auxiliary.compose.repository.ChatRepository
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
import com.vitorpamplona.quartz.nip01Core.tags.people.taggedUsers
import com.vitorpamplona.quartz.nip17Dm.NIP17Factory
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip51Lists.encryption.PrivateTagsInContent
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.Rumor
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealedRumorEvent
import com.vitorpamplona.quartz.nip77Negentropy.NegCloseCmd
import com.vitorpamplona.quartz.nip77Negentropy.NegOpenCmd
import com.vitorpamplona.quartz.utils.TimeUtils
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

class NotaryViewModel(
    val nostrRepository: NostrRepository,
    val chatRepository: ChatRepository,
    val scope: CoroutineScope,
): ViewModel() {

    companion object {
        private const val TAG = "NotaryViewModel"


        fun factory(
            nostrRepository: NostrRepository,
            chatRepository: ChatRepository,
            scope: CoroutineScope,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                NotaryViewModel(
                    nostrRepository = nostrRepository,
                    chatRepository = chatRepository,
                    scope = scope
                )
            }
        }
    }

    private val logger = Logger.withTag(TAG)

    init {
        observeUnsignedNostrEvents()
        observeUnsealedGiftWrapPayloads()
    }


    private fun observeUnsignedNostrEvents() {
        val tempSigner = NostrSignerSync(
            SeedManager.activeKeyPair()
        )
        scope.launch(Dispatchers.IO) {
            logger.i { "observeUnsignedNostrEvents" }
            nostrRepository.observeUnsignedNostrEvents(
                publicKey = SeedManager.activePublicKey().toHexKey()
            ).distinctUntilChanged().collect { unsignedNostrEventOrNull ->
                scope.launch(Dispatchers.IO) {
                    unsignedNostrEventOrNull?.let { unsignedNostrEvent ->
                        logger.d("Unsigned: ${unsignedNostrEvent.kind}")
                        val event = tempSigner.signNormal<Event>(
                            createdAt = unsignedNostrEvent.createdAt.epochSeconds,
                            kind = unsignedNostrEvent.kind,
                            tags = unsignedNostrEvent.tags,
                            content = unsignedNostrEvent.privateTags?.let { PrivateTagsInContent.encryptNip44(it, tempSigner) } ?: unsignedNostrEvent.content
                        )
                        logger.d("Signed: ${event.toJson()}")

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
    }

    private fun observeUnsealedGiftWrapPayloads() {
        val tempSigner = NostrSignerSync(
            SeedManager.activeKeyPair()
        )
        scope.launch(Dispatchers.IO) {
            logger.i { "observeUnsealedGiftWrapPayloads" }
            chatRepository.observeUnsealedGiftWrapPayloads(
                publicKey = SeedManager.activePublicKey().toHexKey()
            ).distinctUntilChanged().collect { giftWrapPayloadOrNull ->
                scope.launch(Dispatchers.IO) {
                    giftWrapPayloadOrNull?.let { giftWrapPayload ->
                        logger.d("seal and deliver giftWrapPayload: $giftWrapPayload")

                        chatRepository.sealGiftWrapPayload(
                            giftWrapPayload,
                            nostrSignerSync = tempSigner
                        )
                    }
                }
            }
        }
    }


}