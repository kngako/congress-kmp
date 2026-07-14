package at.torch.compose.ui.view.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import at.torch.compose.repository.MarmotRepository
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip51Lists.encryption.PrivateTagsInContent
import fr.acinq.phoenix.data.ActiveWallet
import fr.acinq.phoenix.managers.nostrPrivateKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.time.Instant

class NotaryViewModel(
    val activeWalletStateFlow: StateFlow<ActiveWallet?>,
    val nostrRepository: at.torch.compose.repository.NostrRepository,
    val chatRepository: at.torch.compose.repository.ChatRepository,
    val marmotRepository: MarmotRepository,
    val scope: CoroutineScope,
): ViewModel() {

    companion object {
        private const val TAG = "NotaryViewModel"


        fun factory(
            activeWalletStateFlow: StateFlow<ActiveWallet?>,
            nostrRepository: at.torch.compose.repository.NostrRepository,
            chatRepository: at.torch.compose.repository.ChatRepository,
            marmotRepository: MarmotRepository,
            scope: CoroutineScope,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                NotaryViewModel(
                    activeWalletStateFlow = activeWalletStateFlow,
                    nostrRepository = nostrRepository,
                    chatRepository = chatRepository,
                    marmotRepository = marmotRepository,
                    scope = scope,
                )
            }
        }
    }

    private val logger = Logger.withTag(TAG)

    init {
        scope.launch {
            activeWalletStateFlow.collectLatest { activeWallet ->
                logger.d("Notary ActiveWallet: $activeWallet")
                activeWallet?.business?.walletManager?.keyManager?.value?.nostrPrivateKey()?.let { nostrPrivateKey ->

                    val keyPair = KeyPair(
                        privKey = nostrPrivateKey.value.toByteArray()
                    )

                    observeUnsignedNostrEvents(keyPair)
                    observeUnsealedGiftWrapPayloads(keyPair)
                    observeUnprocessedMarmotInnerEvents(keyPair)
                    observeActiveMarmotKeyPackageBundle(keyPair)
                }
            }
        }
    }


    private fun observeUnsignedNostrEvents(
        keyPair: KeyPair
    ) {
        val tempSigner = NostrSignerSync(
            keyPair
        )
        scope.launch(Dispatchers.IO) {
            logger.i { "observeUnsignedNostrEvents: ${keyPair.pubKey.toHexKey()}" }
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
                            _root_ide_package_.at.torch.compose.database.model.NostrEvent(
                                id = event.id,
                                pubKey = event.pubKey,
                                kind = event.kind,
                                tags = event.tags,
                                content = event.content,
                                createdAt = Instant.fromEpochSeconds(event.createdAt),
                                sig = event.sig,
                                unsignedNostrEventId = unsignedNostrEvent.id
                            ),
                            relayURLs = at.torch.compose.nostr.Relays.DefaultDMRelayList.map { normalizedRelayUrl -> normalizedRelayUrl.url },
                            activeKeyPair = keyPair
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
            logger.i { "observeUnsealedGiftWrapPayloads: ${keyPair.pubKey.toHexKey()}" }
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

    private fun observeUnprocessedMarmotInnerEvents(
        keyPair: KeyPair
    ) {
        val tempSigner = NostrSignerSync(
            keyPair
        )
        scope.launch(Dispatchers.IO) {
            logger.i { "observeUnsealedGiftWrapPayloads: ${keyPair.pubKey.toHexKey()}" }
            marmotRepository.observeUnprocessedMarmotInnerEvents(
                publicKey = keyPair.pubKey.toHexKey()
            ).distinctUntilChanged().collect { marmotInnerEventOrNull ->
                scope.launch(Dispatchers.IO) {
                    marmotInnerEventOrNull?.let { marmotInnerEvent ->
                        logger.d("encrypt and broadcast: $marmotInnerEvent")

                        marmotRepository.encryptAndSendMarmotInnerEvent(
                            marmotInnerEvent,
                            nostrSignerSync = tempSigner
                        )
                    }
                }
            }
        }
    }


    private fun observeActiveMarmotKeyPackageBundle(
        keyPair: KeyPair
    ) {
        val tempSigner = NostrSignerSync(
            keyPair
        )
        scope.launch(Dispatchers.IO) {
            logger.i { "observeActiveMarmotKeyPackageBundle: ${keyPair.pubKey.toHexKey()}" }
            marmotRepository.observeActiveMarmotKeyPackageBundle(
                publicKey = keyPair.pubKey.toHexKey()
            ).distinctUntilChanged().collect { marmotKeyPackageBundleOrNull ->
                scope.launch(Dispatchers.IO) {
                    if (marmotKeyPackageBundleOrNull == null) {
                        logger.d("Need to create new key package bundle")
                        marmotRepository.publishMarmotKeyPackageBundle(
                            publicKey = keyPair.pubKey.toHexKey(),
                            nsecPassword = "" // TODO: Implement nsecPassword logic...
                        )
                    }
                }
            }
        }
    }

}