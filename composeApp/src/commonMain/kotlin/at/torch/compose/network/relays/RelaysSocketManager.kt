package at.torch.compose.network.relays

import at.torch.compose.network.dto.mapToRelayDTO
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CloseCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip77Negentropy.NegCloseCmd
import com.vitorpamplona.quartz.nip77Negentropy.NegOpenCmd
import fr.acinq.phoenix.data.ActiveWallet
import fr.acinq.phoenix.managers.nostrPublicKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock


/**
 * As Seen in Primal
 *
 * https://github.com/PrimalHQ/primal-android-app/blob/6db2e6862239335dede4162338d7c4f10ad35031/app/src/main/kotlin/net/primal/android/networking/relays/RelaysSocketManager.kt
 */
class RelaysSocketManager(
    private val activeWalletStateFlow: StateFlow<ActiveWallet?>,
    private val nostrSocketClientFactory: at.torch.compose.network.sockets.NostrSocketClientFactory,
    private val cachingImportRepository: at.torch.compose.repository.CachingImportRepository,
    private val relayRepository: at.torch.compose.repository.RelayRepository,
) {
    val logger = Logger.withTag("RelaysSocketManager")
    private val scope = CoroutineScope(Dispatchers.IO)
    private val relayPoolsMutex = Mutex()

    /**
     * Concrete relay pool
     */
    private val relayPool =  RelayPool(
        nostrSocketClientFactory = nostrSocketClientFactory,
        cachingImportRepository = cachingImportRepository,
    )

    /**
     * Logical relayPool
     */
    private val userRelays = mutableSetOf<at.torch.compose.network.dto.RelayDTO>()

    val userRelayPoolStatus = relayPool.relayPoolStatus

    init {
        observeActiveUserId()
    }

    private val observeRelayJobs =  mutableMapOf<HexKey, Job>()

    private fun observeActiveUserId() =
        scope.launch {
            activeWalletStateFlow.collectLatest { activeWallet ->
                if (activeWallet == null) {
                    // TODO: Cancel all pending jobs?
                }
                activeWallet?.business?.walletManager?.keyManager?.collectLatest { keyManager ->
                    keyManager?.nostrPublicKey()?.let { pubkey ->
                        observeRelayJobs[pubkey]?.cancel()
                        observeRelayJobs[pubkey] = observeRelays(pubkey)
                    }
                }
            }


        }

    private fun observeRelays(publicKey: String): Job =
        scope.launch {
            try {
                relayRepository.observePublicKeyRelays(publicKey = publicKey).collect { relays ->
                    val userRelays = relays.filter { it.type == "user" }.map { it.mapToRelayDTO() }
                    updateRelayPools(regularRelays = userRelays)
                }
            } catch (error: CancellationException) {
                logger.w(throwable = error) { "Relay observation cancelled" }
            }
        }

    private suspend fun updateRelayPools(regularRelays: List<at.torch.compose.network.dto.RelayDTO>?) {
        relayPoolsMutex.withLock {
            val userRelaysChanged = relayPool.relays != regularRelays
            if (userRelaysChanged && !regularRelays.isNullOrEmpty()) {
                relayPool.changeRelays(relays = regularRelays)
            }
        }
    }

    private suspend fun clearRelayPools() =
        relayPoolsMutex.withLock {
            relayPool.closePool()
        }

    @Throws(at.torch.compose.exceptions.NostrPublishException::class)
    suspend fun publishEvent(nostrEvent: at.torch.compose.database.model.NostrEvent): Flow<NostrPublishResult> {
        return if (userRelays.isNotEmpty()) {
            relayPool.publishEvent(
                nostrEvent = nostrEvent,
                relays = userRelays
            )
        } else {
            relayPool.publishEvent(
                nostrEvent = nostrEvent,
                relays = FALLBACK_RELAYS
            )
        }
    }

    @Throws(at.torch.compose.exceptions.NostrPublishException::class)
    suspend fun publishEvent(nostrEvent: at.torch.compose.database.model.NostrEvent, relays: Set<at.torch.compose.network.dto.RelayDTO>): Flow<NostrPublishResult> {
        logger.d("publishEvent: $nostrEvent")
        return relayPool.publishEvent(
            nostrEvent = nostrEvent,
            relays = relays
        )
    }

    fun tryConnectingToAllRelays() {
        relayPool.relays.forEach {
            scope.launch {
                relayPool.tryConnectingToRelay(it.url)
            }
        }
    }

//    suspend fun tryConnectingToUserRelay(url: String) = relayPool.tryConnectingToRelay(url)

    suspend fun query(reqCommand: ReqCmd, relayUrl: String): Flow<at.torch.compose.network.sockets.NostrIncomingMessage> {
        return relayPool.query(
            reqCommand = reqCommand,
            relayUrl = relayUrl
        )
    }


    suspend fun closeQuery(closeCmd: CloseCmd, relayUrl: String) {
        return relayPool.closeQuery(
            closeCmd = closeCmd,
            relayUrl = relayUrl
        )
    }

    suspend fun negentropySync(negOpenCmd: NegOpenCmd, relayUrl: String): Flow<at.torch.compose.network.sockets.NostrIncomingMessage> {
        return relayPool.negentropySync(
            negOpenCmd = negOpenCmd,
            relayUrl = relayUrl
        )
    }

    suspend fun closeNegentropySync(negCloseCmd: NegCloseCmd, relayUrl: String) {
        return relayPool.closeNegentropySync(
            negCloseCmd = negCloseCmd,
            relayUrl = relayUrl
        )
    }
}
