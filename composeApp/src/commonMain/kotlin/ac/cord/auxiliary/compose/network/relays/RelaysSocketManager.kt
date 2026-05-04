package ac.cord.auxiliary.compose.network.relays

import ac.cord.auxiliary.compose.database.model.NostrEvent
import ac.cord.auxiliary.compose.network.dto.RelayDTO
import ac.cord.auxiliary.compose.network.dto.mapToRelayDTO
import ac.cord.auxiliary.compose.network.sockets.NostrSocketClientFactory
import ac.cord.auxiliary.compose.repository.CachingImportRepository
import ac.cord.auxiliary.compose.repository.RelayRepository
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ac.cord.auxiliary.compose.exceptions.NostrPublishException
import ac.cord.auxiliary.compose.managers.SeedManager
import ac.cord.auxiliary.compose.managers.toHex
import ac.cord.auxiliary.compose.network.sockets.NostrIncomingMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CloseCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip77Negentropy.NegCloseCmd
import com.vitorpamplona.quartz.nip77Negentropy.NegOpenCmd
import kotlinx.coroutines.flow.Flow


/**
 * As Seen in Primal
 *
 * https://github.com/PrimalHQ/primal-android-app/blob/6db2e6862239335dede4162338d7c4f10ad35031/app/src/main/kotlin/net/primal/android/networking/relays/RelaysSocketManager.kt
 */
class RelaysSocketManager constructor(
    private val nostrSocketClientFactory: NostrSocketClientFactory,
    private val cachingImportRepository: CachingImportRepository,
    private val relayRepository: RelayRepository
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
    private val userRelays = mutableSetOf<RelayDTO>()

    val userRelayPoolStatus = relayPool.relayPoolStatus

    init {
        observeActiveUserId()
    }

    private val observeRelayJobs =  mutableMapOf<String, Job>()

    private fun observeActiveUserId() =
        scope.launch {
            SeedManager.activePublicKey().toHex()?.let { publicKey ->
                observeRelayJobs[publicKey]?.cancel()

                observeRelayJobs[publicKey] = observeRelays(publicKey)
            }
//            credentialsManager.credentials.collect { credentials ->
//                credentials.forEach { credential ->
//                    credential.npub.bech32ToHexOrNull()?.let { publicKey ->
//                        observeRelayJobs[publicKey]?.cancel()
//
//                        observeRelayJobs[publicKey] = observeRelays(publicKey)
//                    }
//                }
//            }
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

    private suspend fun updateRelayPools(regularRelays: List<RelayDTO>?) {
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

    @Throws(NostrPublishException::class)
    suspend fun publishEvent(nostrEvent: NostrEvent): Flow<NostrPublishResult> {
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

    @Throws(NostrPublishException::class)
    suspend fun publishEvent(nostrEvent: NostrEvent, relays: Set<RelayDTO>): Flow<NostrPublishResult> {
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

    suspend fun query(reqCommand: ReqCmd, relayUrl: String): Flow<NostrIncomingMessage> {
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

    suspend fun negentropySync(negOpenCmd: NegOpenCmd, relayUrl: String): Flow<NostrIncomingMessage> {
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
