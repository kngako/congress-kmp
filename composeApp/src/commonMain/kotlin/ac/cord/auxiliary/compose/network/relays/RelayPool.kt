package ac.cord.auxiliary.compose.network.relays

import ac.cord.auxiliary.compose.database.model.NostrEvent
import ac.cord.auxiliary.compose.exceptions.NetworkException
import ac.cord.auxiliary.compose.exceptions.NostrNoticeException
import ac.cord.auxiliary.compose.network.dto.RelayDTO
import androidx.annotation.VisibleForTesting
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import ac.cord.auxiliary.compose.exceptions.NostrPublishException
import ac.cord.auxiliary.compose.network.dto.toRelayDTO
import ac.cord.auxiliary.compose.network.sockets.NostrIncomingMessage
import ac.cord.auxiliary.compose.network.sockets.NostrSocketClient
import ac.cord.auxiliary.compose.network.sockets.NostrSocketClientFactory
import ac.cord.auxiliary.compose.network.sockets.SocketConnectionClosedCallback
import ac.cord.auxiliary.compose.network.sockets.SocketConnectionOpenedCallback
import ac.cord.auxiliary.compose.network.sockets.filterByEventId
import ac.cord.auxiliary.compose.network.sockets.filterBySubscriptionId
import ac.cord.auxiliary.compose.repository.CachingImportRepository
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CloseCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transformWhile

/**
 * As seen in Primal
 *
 * https://github.com/PrimalHQ/primal-android-app/blob/8a912053131764c39711cd0dc5012645498c9a4f/app/src/main/kotlin/net/primal/android/networking/relays/RelayPool.kt
 */
class RelayPool(
    private val nostrSocketClientFactory: NostrSocketClientFactory,
    private val cachingImportRepository: CachingImportRepository,
) {
    val logger = Logger.withTag("RelayPool")

    companion object {
        const val PUBLISH_TIMEOUT = 30_000
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    val relays: MutableSet<RelayDTO> = mutableSetOf()

    @VisibleForTesting
    var socketClients = setOf<NostrSocketClient>()

    private val _relayPoolStatus = MutableStateFlow(mapOf<String, Boolean>())
    val relayPoolStatus = _relayPoolStatus.asStateFlow()
    private fun updateRelayStatus(url: String, connected: Boolean) =
        scope.launch {
            _relayPoolStatus.getAndUpdate {
                it.toMutableMap().apply { this[url] = connected }
            }
        }

    private val onSocketConnectionOpenedCallback: SocketConnectionOpenedCallback = { url ->
        updateRelayStatus(url = url, connected = true)
    }

    private val onSocketConnectionClosedCallback: SocketConnectionClosedCallback = { url, _ ->
        updateRelayStatus(url = url, connected = false)
    }

    fun changeRelays(relays: List<RelayDTO>) {
        val existingRelayUrls = socketClients.map { it.socketUrl }
        val newRelayUrls = relays.map { it.url }

        val toAddRelayUrls = newRelayUrls.filter { it !in existingRelayUrls }
        val toAddSocketClients = relays.filter { it.url in toAddRelayUrls }.mapAsNostrSocketClient()
        val toRemoveSocketClients = socketClients.filter { it.socketUrl !in newRelayUrls }

        val newSocketClients = socketClients.toMutableSet().apply {
            removeAll(toRemoveSocketClients)
            addAll(toAddSocketClients)
        }

        socketClients = newSocketClients
        toRemoveSocketClients.forEach { client ->
            updateRelayStatus(url = client.socketUrl, connected = false)
            scope.launch { client.close() }
        }
        this.relays.clear()
        this.relays.addAll(relays)
    }

    fun removeRelays(relays: Set<RelayDTO>) {
        val relayUrls = relays.map { it.url }

        val toRemoveSocketClients = socketClients.filter { it.socketUrl in relayUrls }

        val newSocketClients = socketClients.toMutableSet().apply {
            removeAll(toRemoveSocketClients)
        }

        socketClients = newSocketClients
        toRemoveSocketClients.forEach { client ->
            updateRelayStatus(url = client.socketUrl, connected = false)
            scope.launch { client.close() }
        }
        this.relays.removeAll(relays)
    }

    fun addRelaysIfMissing(relays: Set<RelayDTO>) {
        val existingRelayUrls = socketClients.map { it.socketUrl }
        val newRelayUrls = relays.map { it.url }

        val toAddRelayUrls = newRelayUrls.filter { it !in existingRelayUrls }.toSet()
        val toAddSocketClients = relays.filter { it.url in toAddRelayUrls }.mapAsNostrSocketClient()
        logger.d("toAddSocketClients: ${toAddSocketClients.map { it.socketUrl }}" )
        val newSocketClients = socketClients.toMutableSet().apply {
            addAll(toAddSocketClients)
        }
        logger.d("newSocketClients: ${newSocketClients.map { it.socketUrl }}")

        socketClients = newSocketClients

        this.relays.addAll(relays)
    }

    fun closePool() {
        socketClients.forEach { client ->
            updateRelayStatus(url = client.socketUrl, connected = false)
            scope.launch { client.close() }
        }
        socketClients = emptySet()
        relays.clear()
    }

    fun hasRelays() = relays.isNotEmpty()

    suspend fun tryConnectingToRelay(url: String) {
        runCatching {
            socketClients.find { it.socketUrl == url }?.ensureSocketConnectionOrThrow()
        }
    }

    private fun List<RelayDTO>.mapAsNostrSocketClient() =
        this.map {
            nostrSocketClientFactory.create(
                wssUrl = it.url,
                onSocketConnectionOpened = onSocketConnectionOpenedCallback,
                onSocketConnectionClosed = onSocketConnectionClosedCallback,
            )
        }

    @Throws(NostrPublishException::class)
    suspend fun publishEvent(nostrEvent: NostrEvent, relays: Set<RelayDTO> = emptySet()): Flow<NostrPublishResult> {
        val relayUrls = relays.map { it.url }

        return if (relayUrls.isEmpty()) {
            handlePublishEventToRelays(socketClients, nostrEvent)
        } else {
            addRelaysIfMissing(relays)

            val filteredSocketClients = socketClients.filter { relayUrls.contains(it.socketUrl) }

            handlePublishEventToRelays(filteredSocketClients.toSet(), nostrEvent)
        }
    }

    suspend fun query(reqCommand: ReqCmd, relayUrl: String): Flow<NostrIncomingMessage> {
        addRelaysIfMissing(
            setOf(
                NormalizedRelayUrl(relayUrl).url.toRelayDTO()
            )
        )

        logger.d("socketClients: ${socketClients.map { it.socketUrl }}")
        val nostrSocketClient = socketClients.find { NormalizedRelayUrl(it.socketUrl).displayUrl() == NormalizedRelayUrl(relayUrl).displayUrl() }

        val filterRequest = OptimizedJsonMapper.toJson(reqCommand)

        if (nostrSocketClient == null) {
            throw NetworkException("$relayUrl is not connected")
        }
        return coroutineScope {
            val eventFlow =  nostrSocketClient.queryAsFlow(reqCommand.subId)
            with(nostrSocketClient) {
                sendMESSAGE(filterRequest)
            }
            eventFlow
        }
    }

    suspend fun closeQuery(closeCmd: CloseCmd, relayUrl: String) {
        addRelaysIfMissing(
            setOf(
                NormalizedRelayUrl(relayUrl).url.toRelayDTO()
            )
        )

        logger.d("socketClients: ${socketClients.map { it.socketUrl }}")
        val nostrSocketClient = socketClients.find { NormalizedRelayUrl(it.socketUrl).displayUrl() == NormalizedRelayUrl(relayUrl).displayUrl() }

        val closeSubscription = OptimizedJsonMapper.toJson(closeCmd)

        if (nostrSocketClient == null) {
            throw NetworkException("$relayUrl is not connected")
        }
        coroutineScope {
            with(nostrSocketClient) {
                sendMESSAGE(closeSubscription)
            }
        }
    }

    private fun Flow<NostrIncomingMessage>.transformWhileEventsAreIncoming() =
        transformWhile {
            emit(it)
            it is NostrIncomingMessage.EventMessage || it is NostrIncomingMessage.EventsMessage
        }

    @OptIn(FlowPreview::class)
    private suspend fun NostrSocketClient.queryAsFlow(subscriptionId: String): Flow<NostrIncomingMessage> {
        return this.incomingMessages
            .filterBySubscriptionId(id = subscriptionId)
//            .transformWhileEventsAreIncoming()
    }

    @OptIn(FlowPreview::class)
    private suspend fun handlePublishEventToRelays(relayConnections: Set<NostrSocketClient>, nostrEvent: NostrEvent): Flow<NostrPublishResult> {
        val responseFlow = MutableSharedFlow<NostrPublishResult>()
        relayConnections.forEach { nostrSocketClient ->
            scope.launch {
                with(nostrSocketClient) {
                    val sendEventResult = runCatching {
                        ensureSocketConnectionOrThrow()
                        sendEVENT(nostrEvent.toNostrJsonObject())
                        collectPublishResponse(eventId = nostrEvent.id)
                    }
                    sendEventResult.getOrNull()?.let {
                        responseFlow.emit(
                            NostrPublishResult(
                                result = it,
                                relayUrl = nostrSocketClient.socketUrl
                            )
                        )
                    }
                    sendEventResult.exceptionOrNull()?.let {
                        logger.w(throwable = it) { "sendEVENT failed to $socketUrl" }
                        responseFlow.emit(
                            NostrPublishResult(
                                error = it,
                                relayUrl = nostrSocketClient.socketUrl
                            )
                        )
                    }
                }
            }
        }

//        var responseCount = 0
//        responseFlow.timeout(PUBLISH_TIMEOUT.milliseconds)
//            .catch {
//                logger.e("Caught a nostr publish exception: $it")
//                throw NostrPublishException(cause = it)
//            }
//            .transform {
//                emit(it)
//                responseCount++
//                if (relayConnections.size == responseCount && !it.isSuccessful()) {
//                    throw NostrPublishException(cause = null)
//                }
//            }
//            .first { it.isSuccessful() }
        return responseFlow
    }

    @FlowPreview
    private suspend fun NostrSocketClient.collectPublishResponse(eventId: String): NostrIncomingMessage.OkMessage {
        return incomingMessages
            .filterByEventId(id = eventId)
            .transform {
                when (it) {
                    is NostrIncomingMessage.OkMessage -> emit(it)
                    is NostrIncomingMessage.NoticeMessage -> throw NostrNoticeException(reason = it.message)
                    else -> error("$it is not allowed")
                }
            }
            .timeout(PUBLISH_TIMEOUT.milliseconds)
            .first()
    }

    private fun NostrPublishResult.isSuccessful(): Boolean {
        return result is NostrIncomingMessage.OkMessage && result.success
    }
}
