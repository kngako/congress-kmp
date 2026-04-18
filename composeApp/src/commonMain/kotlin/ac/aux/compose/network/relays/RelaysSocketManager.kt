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

    private fun buildRelayPool() =
        RelayPool(
            nostrSocketClientFactory = nostrSocketClientFactory,
            cachingImportRepository = cachingImportRepository,
        )

    private val userRelaysPool: RelayPool = buildRelayPool()
    private val nwcRelaysPool: RelayPool = buildRelayPool()
    private val fallbackRelaysPool: RelayPool = buildRelayPool()

    val userRelayPoolStatus = userRelaysPool.relayPoolStatus

    init {
        initFallbackRelaysPool()
        observeActiveUserId()
    }

    private fun initFallbackRelaysPool() = fallbackRelaysPool.changeRelays(FALLBACK_RELAYS)

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
                    val userRelays = relays.filter { it.type != "nwc" }.map { it.mapToRelayDTO() }
                    val nwcRelays = relays.filter { it.type == "nwc" }.map { it.mapToRelayDTO() }
                    updateRelayPools(regularRelays = userRelays, walletRelays = nwcRelays)
                }
            } catch (error: CancellationException) {
                logger.w(throwable = error) { "Relay observation cancelled" }
            }
        }

    private suspend fun updateRelayPools(regularRelays: List<RelayDTO>?, walletRelays: List<RelayDTO>?) {
        relayPoolsMutex.withLock {
            val userRelaysChanged = userRelaysPool.relays != regularRelays
            if (userRelaysChanged && !regularRelays.isNullOrEmpty()) {
                userRelaysPool.changeRelays(relays = regularRelays)
            }

            val nwcRelaysChanged = nwcRelaysPool.relays != walletRelays
            if (nwcRelaysChanged && !walletRelays.isNullOrEmpty()) {
                nwcRelaysPool.changeRelays(relays = walletRelays)
            }
        }
    }

    private suspend fun clearRelayPools() =
        relayPoolsMutex.withLock {
            userRelaysPool.closePool()
            nwcRelaysPool.closePool()
        }

    @Throws(NostrPublishException::class)
    suspend fun publishEvent(nostrEvent: NostrEvent) {
        if (userRelaysPool.hasRelays()) {
            userRelaysPool.publishEvent(nostrEvent = nostrEvent, cachingProxyEnabled = isCachingProxyEnabled())
        } else {
            fallbackRelaysPool.publishEvent(nostrEvent = nostrEvent, cachingProxyEnabled = isCachingProxyEnabled())
        }
    }

    @Throws(NostrPublishException::class)
    suspend fun publishEvent(nostrEvent: NostrEvent, relays: List<RelayDTO>) {
        val customPool = buildRelayPool()
        customPool.changeRelays(relays = relays)
        customPool.publishEvent(nostrEvent = nostrEvent, cachingProxyEnabled = isCachingProxyEnabled())
        customPool.closePool()
    }

    @Throws(NostrPublishException::class)
    suspend fun publishNwcEvent(nostrEvent: NostrEvent) {
        if (!nwcRelaysPool.hasRelays()) {
            throw NostrPublishException(cause = IllegalStateException("nwc relay not found"))
        }

        nwcRelaysPool.publishEvent(nostrEvent = nostrEvent, cachingProxyEnabled = isCachingProxyEnabled())
    }

    fun tryConnectingToAllUserRelays() {
        userRelaysPool.relays.forEach {
            scope.launch {
                userRelaysPool.tryConnectingToRelay(it.url)
            }
        }
    }

    suspend fun tryConnectingToUserRelay(url: String) = userRelaysPool.tryConnectingToRelay(url)
}
