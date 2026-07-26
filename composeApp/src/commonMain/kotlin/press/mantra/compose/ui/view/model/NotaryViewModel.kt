package press.mantra.compose.ui.view.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import press.mantra.compose.repository.MarmotRepository
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip51Lists.encryption.PrivateTagsInContent
import fr.acinq.phoenix.data.ActiveWallet
import fr.acinq.phoenix.managers.nostrPrivateKey
import fr.acinq.phoenix.managers.nsecPassword
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import press.mantra.compose.database.model.NostrEvent
import kotlin.time.Instant

class NotaryViewModel(
    val activeWalletStateFlow: StateFlow<ActiveWallet?>,
    val nostrRepository: press.mantra.compose.repository.NostrRepository,
    val chatRepository: press.mantra.compose.repository.ChatRepository,
    val marmotRepository: MarmotRepository,
    val scope: CoroutineScope,
): ViewModel() {

    companion object {
        private const val TAG = "NotaryViewModel"


        fun factory(
            activeWalletStateFlow: StateFlow<ActiveWallet?>,
            nostrRepository: press.mantra.compose.repository.NostrRepository,
            chatRepository: press.mantra.compose.repository.ChatRepository,
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

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeActiveNostrKey() {
        scope.launch {
            activeWalletStateFlow
                .flatMapLatest { activeWallet ->
                    activeWallet?.business?.walletManager?.keyManager ?: flowOf(null)
                }
                .map { keyManager -> keyManager?.nostrPrivateKey() }
                .distinctUntilChanged()
                .collectLatest { nostrPrivateKey ->
                    if (nostrPrivateKey == null) return@collectLatest

                    val keyPair = KeyPair(
                        privKey = nostrPrivateKey.value.toByteArray()
                    )
                    val nsecPassword = nostrPrivateKey.nsecPassword()
                    logger.d("Notary observing: ${keyPair.pubKey.toHexKey()}")

                    // The observers run as children of collectLatest: when the active
                    // key changes they are cancelled, so they never process events for
                    // a stale wallet and never run in duplicate.
                    coroutineScope {
                        launch { observeUnsignedNostrEvents(keyPair) }
                        launch { observeUnsealedGiftWrapPayloads(keyPair) }
                        launch { observeUnprocessedMarmotInnerEvents(keyPair) }
                        launch { observeActiveMarmotKeyPackageBundle(keyPair, nsecPassword) }
                    }
                }
        }
    }

    init {
        observeActiveNostrKey()
    }

    // Keeps one failing item from killing the collector for the rest of the session.
    private inline fun guardNotarization(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.e("Notarization failed: $what", e)
        }
    }

    private suspend fun observeUnsignedNostrEvents(
        keyPair: KeyPair
    ) {
        val tempSigner = NostrSignerSync(
            keyPair
        )
        logger.i { "observeUnsignedNostrEvents: ${keyPair.pubKey.toHexKey()}" }
        nostrRepository.observeUnsignedNostrEvents(
            publicKey = keyPair.pubKey.toHexKey()
        ).distinctUntilChanged().collect { unsignedNostrEventOrNull ->
            unsignedNostrEventOrNull?.let { unsignedNostrEvent ->
                guardNotarization("unsigned event ${unsignedNostrEvent.id} (kind ${unsignedNostrEvent.kind})") {
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
                        relayURLs = press.mantra.compose.nostr.Relays.DefaultDMRelayList.map { normalizedRelayUrl -> normalizedRelayUrl.url },
                        activeKeyPair = keyPair
                    )
                }
            }
        }
    }


    private suspend fun observeUnsealedGiftWrapPayloads(
        keyPair: KeyPair
    ) {
        val tempSigner = NostrSignerSync(
            keyPair
        )
        logger.i { "observeUnsealedGiftWrapPayloads: ${keyPair.pubKey.toHexKey()}" }
        chatRepository.observeUnsealedGiftWrapPayloads(
            publicKey = keyPair.pubKey.toHexKey()
        ).distinctUntilChanged().collect { giftWrapPayloadOrNull ->
            giftWrapPayloadOrNull?.let { giftWrapPayload ->
                guardNotarization("gift wrap payload ${giftWrapPayload.id}") {
                    logger.d("seal and deliver giftWrapPayload: $giftWrapPayload")

                    chatRepository.sealGiftWrapPayload(
                        giftWrapPayload,
                        nostrSignerSync = tempSigner
                    )
                }
            }
        }
    }

    private suspend fun observeUnprocessedMarmotInnerEvents(
        keyPair: KeyPair
    ) {
        val tempSigner = NostrSignerSync(
            keyPair
        )
        logger.i { "observeUnprocessedMarmotInnerEvents: ${keyPair.pubKey.toHexKey()}" }
        marmotRepository.observeUnprocessedMarmotInnerEvents(
            publicKey = keyPair.pubKey.toHexKey()
        ).distinctUntilChanged().collect { marmotInnerEventOrNull ->
            marmotInnerEventOrNull?.let { marmotInnerEvent ->
                guardNotarization("marmot inner event ${marmotInnerEvent.id}") {
                    logger.d("encrypt and broadcast: $marmotInnerEvent")

                    marmotRepository.encryptAndSendMarmotInnerEvent(
                        marmotInnerEvent,
                        nostrSignerSync = tempSigner
                    )
                }
            }
        }
    }


    private suspend fun observeActiveMarmotKeyPackageBundle(
        keyPair: KeyPair,
        nsecPassword: String
    ) {
        logger.i { "observeActiveMarmotKeyPackageBundle: ${keyPair.pubKey.toHexKey()}" }
        marmotRepository.observeActiveMarmotKeyPackageBundle(
            publicKey = keyPair.pubKey.toHexKey()
        ).distinctUntilChanged().collect { marmotKeyPackageBundleOrNull ->
            if (marmotKeyPackageBundleOrNull == null) {
                guardNotarization("key package bundle for ${keyPair.pubKey.toHexKey()}") {
                    logger.d("Need to create new key package bundle")
                    marmotRepository.publishMarmotKeyPackageBundle(
                        publicKey = keyPair.pubKey.toHexKey(),
                        nsecPassword = nsecPassword
                    )
                }
            }
        }
    }

}
