package ac.aux.compose.network.relays

import ac.aux.compose.database.model.NostrEvent
import ac.aux.compose.network.dto.RelayDTO
import ac.aux.compose.network.dto.mapToRelayDTO
import ac.aux.compose.network.sockets.NostrSocketClientFactory
import ac.aux.compose.repository.CachingImportRepository
import ac.aux.compose.repository.RelayRepository
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ac.aux.compose.exceptions.NostrPublishException


/**
 * As Seen in Primal
 *
 * https://github.com/PrimalHQ/primal-android-app/blob/6db2e6862239335dede4162338d7c4f10ad35031/app/src/main/kotlin/net/primal/android/networking/relays/RelaysSocketManager.kt
 */
class RelaysSocketManager constructor(
    private val nostrSocketClientFactory: NostrSocketClientFactory,
    private val cachingImportRepository: CachingImportRepository,
//    private val activeAccountStore: ActiveAccountStore,
//    private val usersDatabase: UsersDatabase,
    private val relayRepository: RelayRepository
) {
    val logger = Logger.withTag("RelaysSocketManager")
    private val scope = CoroutineScope(Dispatchers.IO)
    private val relayPoolsMutex = Mutex()

    private var relaysObserverJob: Job? = null


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


    private fun observeActiveUserId() =
        scope.launch {
//          TODO activeAccountStore.activeUserId.collect { userId ->
//                when {
//                    userId.isEmpty() -> {
//                        relaysObserverJob?.cancel()
//                        relaysObserverJob = null
//                        clearRelayPools()
//                    }
//
//                    else -> {
//                        relaysObserverJob?.cancel()
//                        relaysObserverJob = observeRelays(userId)
//                    }
//                }
//            }
        }

    private suspend fun isCachingProxyEnabled() = false // TODO: activeAccountStore.activeUserAccount().cachingProxyEnabled

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
    suspend fun publishEvent(nostrEvent: NostrEvent) {
        if (userRelays.isNotEmpty()) {
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
    suspend fun publishEvent(nostrEvent: NostrEvent, relays: Set<RelayDTO>) {
        relayPool.publishEvent(
            nostrEvent = nostrEvent,
            relays = relays
        )

        // TODO: Close relays and remove them from relayPool
//        customPool.closePool()
    }

    fun tryConnectingToAllUserRelays() {
        relayPool.relays.forEach {
            scope.launch {
                relayPool.tryConnectingToRelay(it.url)
            }
        }
    }

    suspend fun tryConnectingToUserRelay(url: String) = relayPool.tryConnectingToRelay(url)
}
