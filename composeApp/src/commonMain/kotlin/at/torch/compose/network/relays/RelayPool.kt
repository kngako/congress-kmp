package at.torch.compose.network.relays

import at.torch.compose.exceptions.NetworkException
import at.torch.compose.exceptions.NostrNoticeException
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
import at.torch.compose.network.dto.toRelayDTO
import at.torch.compose.network.sockets.filterByEventId
import at.torch.compose.network.sockets.filterBySubscriptionId
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CloseCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip77Negentropy.NegCloseCmd
import com.vitorpamplona.quartz.nip77Negentropy.NegOpenCmd
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * As seen in Primal
 *
 * https://github.com/PrimalHQ/primal-android-app/blob/8a912053131764c39711cd0dc5012645498c9a4f/app/src/main/kotlin/net/primal/android/networking/relays/RelayPool.kt
 */
class RelayPool(
    private val nostrSocketClientFactory: at.torch.compose.network.sockets.NostrSocketClientFactory,
    private val cachingImportRepository: at.torch.compose.repository.CachingImportRepository,
) {
    val logger = Logger.withTag("RelayPool")

    companion object {
        const val PUBLISH_TIMEOUT = 30_000
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    val relays: MutableSet<at.torch.compose.network.dto.RelayDTO> = mutableSetOf()

    private val relayMutex = Mutex()

    @VisibleForTesting
    var socketClients = setOf<at.torch.compose.network.sockets.NostrSocketClient>()

    private val _relayPoolStatus = MutableStateFlow(mapOf<String, Boolean>())
    val relayPoolStatus = _relayPoolStatus.asStateFlow()
    private fun updateRelayStatus(url: String, connected: Boolean) =
        scope.launch {
            _relayPoolStatus.getAndUpdate {
                it.toMutableMap().apply { this[url] = connected }
            }
        }

    private val onSocketConnectionOpenedCallback: at.torch.compose.network.sockets.SocketConnectionOpenedCallback = { url ->
        updateRelayStatus(url = url, connected = true)
    }

    private val onSocketConnectionClosedCallback: at.torch.compose.network.sockets.SocketConnectionClosedCallback = { url, _ ->
        updateRelayStatus(url = url, connected = false)
    }

    fun changeRelays(relays: List<at.torch.compose.network.dto.RelayDTO>) {
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

    fun removeRelays(relays: Set<at.torch.compose.network.dto.RelayDTO>) {
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

    suspend fun addRelaysIfMissing(relays: Set<at.torch.compose.network.dto.RelayDTO>) {
        relayMutex.withLock {
            val existingRelayUrls = socketClients.map { NormalizedRelayUrl(it.socketUrl).displayUrl() }
            val newRelayUrls = relays.map { it.url }

            val toAddRelayUrls = newRelayUrls.filter { NormalizedRelayUrl(it).displayUrl() !in existingRelayUrls }.toSet()
            val toAddSocketClients = relays.filter { it.url in toAddRelayUrls }.mapAsNostrSocketClient()
            if (toAddSocketClients.isNotEmpty()) {
                val newSocketClients = socketClients.toMutableSet().apply {
                    addAll(toAddSocketClients)
                }
                logger.d("newSocketClients: ${newSocketClients.map { it.socketUrl }}")

                socketClients = newSocketClients

                this.relays.addAll(relays)
            }
        }
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

    private fun List<at.torch.compose.network.dto.RelayDTO>.mapAsNostrSocketClient() =
        this.map {
            nostrSocketClientFactory.create(
                wssUrl = it.url,
                onSocketConnectionOpened = onSocketConnectionOpenedCallback,
                onSocketConnectionClosed = onSocketConnectionClosedCallback,
            )
        }

    @Throws(_root_ide_package_.at.torch.compose.exceptions.NostrPublishException::class)
    suspend fun publishEvent(nostrEvent: at.torch.compose.database.model.NostrEvent, relays: Set<at.torch.compose.network.dto.RelayDTO> = emptySet()): Flow<NostrPublishResult> {
        val relayUrls = relays.map { NormalizedRelayUrl(it.url).displayUrl() }

        return if (relayUrls.isEmpty()) {
            logger.w("Empty Relays")
            handlePublishEventToRelays(socketClients, nostrEvent)
        } else {
            logger.d("Add Relays: $relays")
            addRelaysIfMissing(relays)

            val filteredSocketClients = socketClients.filter { NormalizedRelayUrl(it.socketUrl).displayUrl() in relayUrls }
            logger.d("filteredSocketClients: ${filteredSocketClients.map { it.socketUrl }}")

            handlePublishEventToRelays(filteredSocketClients.toSet(), nostrEvent)
        }
    }

    suspend fun query(reqCommand: ReqCmd, relayUrl: String): Flow<at.torch.compose.network.sockets.NostrIncomingMessage> {
        addRelaysIfMissing(
            setOf(
                NormalizedRelayUrl(relayUrl).url.toRelayDTO()
            )
        )

        logger.d("socketClients: ${socketClients.map { it.socketUrl }}")
        val nostrSocketClient = socketClients.find { NormalizedRelayUrl(it.socketUrl).displayUrl() == NormalizedRelayUrl(relayUrl).displayUrl() }

        val filterRequest = OptimizedJsonMapper.toJson(reqCommand)

        if (nostrSocketClient == null) {
            throw _root_ide_package_.at.torch.compose.exceptions.NetworkException("$relayUrl is not connected")
        }
        return coroutineScope {
            val eventFlow =  nostrSocketClient.queryAsFlow(reqCommand.subId)
            with(nostrSocketClient) {
                sendMESSAGE(filterRequest)
            }
            eventFlow
        }
    }

    suspend fun negentropySync(negOpenCmd: NegOpenCmd, relayUrl: String): Flow<at.torch.compose.network.sockets.NostrIncomingMessage> {
        addRelaysIfMissing(
            setOf(
                NormalizedRelayUrl(relayUrl).url.toRelayDTO()
            )
        )

        logger.d("socketClients: ${socketClients.map { it.socketUrl }}")
        val nostrSocketClient = socketClients.find { NormalizedRelayUrl(it.socketUrl).displayUrl() == NormalizedRelayUrl(relayUrl).displayUrl() }

        val negentropySyncRequest = OptimizedJsonMapper.toJson(negOpenCmd)

        if (nostrSocketClient == null) {
            throw _root_ide_package_.at.torch.compose.exceptions.NetworkException("$relayUrl is not connected")
        }
        return coroutineScope {
            val eventFlow =  nostrSocketClient.queryAsFlow(negOpenCmd.subId)
            with(nostrSocketClient) {
                sendMESSAGE(negentropySyncRequest)
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

        val nostrSocketClient = socketClients.find { NormalizedRelayUrl(it.socketUrl).displayUrl() == NormalizedRelayUrl(relayUrl).displayUrl() }

        val closeSubscription = OptimizedJsonMapper.toJson(closeCmd)

        if (nostrSocketClient == null) {
            throw _root_ide_package_.at.torch.compose.exceptions.NetworkException("$relayUrl is not connected")
        }
        coroutineScope {
            with(nostrSocketClient) {
                sendMESSAGE(closeSubscription)
            }
        }
    }

    suspend fun closeNegentropySync(negCloseCmd: NegCloseCmd, relayUrl: String) {
        addRelaysIfMissing(
            setOf(
                NormalizedRelayUrl(relayUrl).url.toRelayDTO()
            )
        )

        logger.d("socketClients: ${socketClients.map { it.socketUrl }}")
        val nostrSocketClient = socketClients.find { NormalizedRelayUrl(it.socketUrl).displayUrl() == NormalizedRelayUrl(relayUrl).displayUrl() }

        val closeNegentropySubscription = OptimizedJsonMapper.toJson(negCloseCmd)

        if (nostrSocketClient == null) {
            throw _root_ide_package_.at.torch.compose.exceptions.NetworkException("$relayUrl is not connected")
        }
        coroutineScope {
            with(nostrSocketClient) {
                sendMESSAGE(closeNegentropySubscription)
            }
        }
    }

    private fun Flow<at.torch.compose.network.sockets.NostrIncomingMessage>.transformWhileEventsAreIncoming() =
        transformWhile {
            emit(it)
            it is at.torch.compose.network.sockets.NostrIncomingMessage.EventMessage || it is at.torch.compose.network.sockets.NostrIncomingMessage.EventsMessage
        }

    @OptIn(FlowPreview::class)
    private suspend fun at.torch.compose.network.sockets.NostrSocketClient.queryAsFlow(subscriptionId: String): Flow<at.torch.compose.network.sockets.NostrIncomingMessage> {
        return this.incomingMessages
            .filterBySubscriptionId(id = subscriptionId)
//            .transformWhileEventsAreIncoming()
    }

    @OptIn(FlowPreview::class)
    private suspend fun handlePublishEventToRelays(relayConnections: Set<at.torch.compose.network.sockets.NostrSocketClient>, nostrEvent: at.torch.compose.database.model.NostrEvent): Flow<NostrPublishResult> {
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
    private suspend fun at.torch.compose.network.sockets.NostrSocketClient.collectPublishResponse(eventId: String): at.torch.compose.network.sockets.NostrIncomingMessage.OkMessage {
        return incomingMessages
            .filterByEventId(id = eventId)
            .transform {
                when (it) {
                    is at.torch.compose.network.sockets.NostrIncomingMessage.OkMessage -> emit(it)
                    is at.torch.compose.network.sockets.NostrIncomingMessage.NoticeMessage -> throw _root_ide_package_.at.torch.compose.exceptions.NostrNoticeException(
                        reason = it.message
                    )
                    else -> error("$it is not allowed")
                }
            }
            .timeout(PUBLISH_TIMEOUT.milliseconds)
            .first()
    }

    private fun NostrPublishResult.isSuccessful(): Boolean {
        return result is at.torch.compose.network.sockets.NostrIncomingMessage.OkMessage && result.success
    }
}
