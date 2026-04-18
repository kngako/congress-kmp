package ac.aux.compose.network.relays

import ac.aux.compose.database.model.NostrEvent
import ac.aux.compose.exceptions.NetworkException
import ac.aux.compose.exceptions.NostrNoticeException
import ac.aux.compose.network.dto.RelayDTO
import androidx.annotation.VisibleForTesting
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import ac.aux.compose.exceptions.NostrPublishException
import ac.aux.compose.network.sockets.NostrIncomingMessage
import ac.aux.compose.network.sockets.NostrSocketClient
import ac.aux.compose.network.sockets.NostrSocketClientFactory
import ac.aux.compose.network.sockets.SocketConnectionClosedCallback
import ac.aux.compose.network.sockets.SocketConnectionOpenedCallback
import ac.aux.compose.network.sockets.filterByEventId
import ac.aux.compose.network.sockets.parseIncomingMessage
import ac.aux.compose.repository.CachingImportRepository
import co.touchlab.kermit.Logger

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
    var socketClients = listOf<NostrSocketClient>()

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

        val newSocketClients = socketClients.toMutableList().apply {
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

        val newSocketClients = socketClients.toMutableList().apply {
            removeAll(toRemoveSocketClients)
        }

        socketClients = newSocketClients
        toRemoveSocketClients.forEach { client ->
            updateRelayStatus(url = client.socketUrl, connected = false)
            scope.launch { client.close() }
        }
        this.relays.removeAll(relays)
    }

    fun addRelays(relays: Set<RelayDTO>) {
        val existingRelayUrls = socketClients.map { it.socketUrl }
        val newRelayUrls = relays.map { it.url }

        val toAddRelayUrls = newRelayUrls.filter { it !in existingRelayUrls }
        val toAddSocketClients = relays.filter { it.url in toAddRelayUrls }.mapAsNostrSocketClient()

        val newSocketClients = socketClients.toMutableList().apply {
            addAll(toAddSocketClients)
        }

        socketClients = newSocketClients

        this.relays.addAll(relays)
    }

    fun closePool() {
        socketClients.forEach { client ->
            updateRelayStatus(url = client.socketUrl, connected = false)
            scope.launch { client.close() }
        }
        socketClients = emptyList()
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
    suspend fun publishEvent(nostrEvent: NostrEvent, relays: Set<RelayDTO> = emptySet()) {
        val relayUrls = relays.map { it.url }

        if (relayUrls.isEmpty()) {
            handlePublishEventToRelays(socketClients, nostrEvent)
        } else {
            addRelays(relays)

            val filteredSocketClients = socketClients.filter { relayUrls.contains(it.socketUrl) }

            handlePublishEventToRelays(filteredSocketClients, nostrEvent)
        }
    }

    private suspend fun handleBroadcastEventThroughCachingProxy(relayUrls: List<String>, nostrEvent: NostrEvent) {
        val result =
            cachingImportRepository.broadcastEvents(
                events = listOf(nostrEvent),
                relays = relayUrls,
            ).getOrNull()
                ?: throw NostrPublishException(
                    cause = NetworkException(message = "Primal NostrEvent 10_000_149 not found or invalid."),
                )

        result.find { response -> response.eventId == nostrEvent.id }?.responses
            ?.mapNotNull { relayResponse ->
                val relay = relayResponse.firstOrNull()
                val responseMessage = relayResponse.getOrNull(index = 1)?.parseIncomingMessage()
                if (relay != null && responseMessage != null) {
                    relay to responseMessage
                } else {
                    null
                }
            }
            ?.find { (_, relayMessage) -> relayMessage is NostrIncomingMessage.OkMessage && relayMessage.success }
            ?: throw NostrPublishException(
                cause = NetworkException("Event broadcast failed. Could not find success response from relays."),
            )
    }

    @OptIn(FlowPreview::class)
    private suspend fun handlePublishEventToRelays(relayConnections: List<NostrSocketClient>, nostrEvent: NostrEvent) {
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
                        responseFlow.emit(NostrPublishResult(result = it))
                    }
                    sendEventResult.exceptionOrNull()?.let {
                        logger.w(throwable = it) { "sendEVENT failed to $socketUrl" }
                        responseFlow.emit(NostrPublishResult(error = it))
                    }
                }
            }
        }

        var responseCount = 0
        responseFlow.timeout(PUBLISH_TIMEOUT.milliseconds)
            .catch { throw NostrPublishException(cause = it) }
            .transform {
                emit(it)
                responseCount++
                if (relayConnections.size == responseCount && !it.isSuccessful()) {
                    throw NostrPublishException(cause = null)
                }
            }
            .first { it.isSuccessful() }
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
