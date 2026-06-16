package ac.cord.auxiliary.compose.ui.view.model

import ac.cord.auxiliary.compose.database.model.NostrEvent
import ac.cord.auxiliary.compose.nostr.Relays
import ac.cord.auxiliary.compose.repository.ChatRepository
import ac.cord.auxiliary.compose.repository.NostrRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip51Lists.encryption.PrivateTagsInContent
import fr.acinq.phoenix.managers.WalletManager
import fr.acinq.phoenix.managers.nostrPrivateKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.time.Instant

class NotaryViewModel(
    val nostrRepository: NostrRepository,
    val chatRepository: ChatRepository,
    val scope: CoroutineScope,
    val walletManager: WalletManager,
): ViewModel() {

    companion object {
        private const val TAG = "NotaryViewModel"


        fun factory(
            nostrRepository: NostrRepository,
            chatRepository: ChatRepository,
            walletManager: WalletManager,
            scope: CoroutineScope,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                NotaryViewModel(
                    nostrRepository = nostrRepository,
                    chatRepository = chatRepository,
                    scope = scope,
                    walletManager = walletManager
                )
            }
        }
    }

    private val logger = Logger.withTag(TAG)

    init {
        walletManager.keyManager.value?.nostrPrivateKey()?.let { nostrPrivateKey ->
            val keyPair = KeyPair(
                privKey = nostrPrivateKey.value.toByteArray()
            )

            observeUnsignedNostrEvents(keyPair)
            observeUnsealedGiftWrapPayloads(keyPair)
        }
    }


    private fun observeUnsignedNostrEvents(
        keyPair: KeyPair
    ) {
            val tempSigner = NostrSignerSync(
                keyPair
            )
            scope.launch(Dispatchers.IO) {
                logger.i { "observeUnsignedNostrEvents" }
                nostrRepository.observeUnsignedNostrEvents(
                    publicKey = keyPair.pubKey.toHexKey()
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
                                relayURLs = Relays.eventPublishRelaySet.map { normalizedRelayUrl -> normalizedRelayUrl.url },
                                walletManager = walletManager
                            )
                        }
                    }
                }
            }
        }


    private fun observeUnsealedGiftWrapPayloads(
        keyPair: KeyPair
    ) {
        val tempSigner = NostrSignerSync(
            keyPair
        )
        scope.launch(Dispatchers.IO) {
            logger.i { "observeUnsealedGiftWrapPayloads" }
            chatRepository.observeUnsealedGiftWrapPayloads(
                publicKey = keyPair.pubKey.toHexKey()
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