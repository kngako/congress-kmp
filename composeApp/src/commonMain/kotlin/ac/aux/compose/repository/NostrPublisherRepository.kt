package ac.aux.compose.repository

import ac.aux.compose.database.model.NostrEvent
import ac.aux.compose.database.model.UnsignedNostrEvent
import ac.aux.compose.exceptions.SignatureException
import ac.aux.compose.network.dto.RelayDTO
import ac.aux.compose.network.relays.RelaysSocketManager
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ac.aux.compose.exceptions.NostrPublishException

/**
 * As Seen in Primal
 *
 * https://github.com/PrimalHQ/primal-android-app/blob/ec22916b4e3472a20bade82d53957a7ef9b708b0/app/src/main/kotlin/net/primal/android/nostr/publish/NostrPublisher.kt#L20
 */
class NostrPublisherRepository(
    private val relaysSocketManager: RelaysSocketManager,
    private val nostrNotary: NostrNotaryRepository,
    private val cachingImportRepository: CachingImportRepository,
) {
    val logger = Logger.withTag("NostrPublisherRepository")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun importEvent(event: NostrEvent) {
        scope.launch {
            runCatching {
                cachingImportRepository.importEvents(events = listOf(event))
            }.onFailure { error ->
                logger.w(throwable = error) { "Failed to import event ${event.id} to caching server." }
            }
        }
    }

    @Throws(NostrPublishException::class)
    private suspend fun publishAndImportEvent(signedNostrEvent: NostrEvent, outboxRelays: List<String> = emptyList()) {
        relaysSocketManager.publishEvent(signedNostrEvent)
        importEvent(signedNostrEvent)
        if (outboxRelays.isNotEmpty()) {
            runCatching {
                relaysSocketManager.publishEvent(
                    nostrEvent = signedNostrEvent,
                    relays = outboxRelays.map { RelayDTO(url = it, read = false, write = true) }.toSet(),
                )
            }.onFailure { error ->
                logger.w(throwable = error) { "Failed to publish to outbox relays." }
            }
        }
    }

    @Throws(NostrPublishException::class, SignatureException::class)
    suspend fun signPublishImportNostrEvent(
        unsignedNostrEvent: UnsignedNostrEvent,
        outboxRelays: List<String>,
    ): NostrEvent {
        val signedNostrEvent = nostrNotary.signNostrEvent(unsignedNostrEvent = unsignedNostrEvent).unwrapOrThrow()
        publishAndImportEvent(signedNostrEvent = signedNostrEvent, outboxRelays = outboxRelays)
        return signedNostrEvent
    }


    @Throws(NostrPublishException::class, SignatureException::class)
    suspend fun publishRelayList(userId: String, relays: List<RelayDTO>): NostrEvent {
        val signedNostrEvent = nostrNotary.signRelayListMetadata(userId = userId, relays = relays).unwrapOrThrow()
        relaysSocketManager.publishEvent(nostrEvent = signedNostrEvent, relays = relays.toSet())
        importEvent(signedNostrEvent)
        return signedNostrEvent
    }
}