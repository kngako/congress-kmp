package at.torch.compose.repository

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * As Seen in Primal
 *
 * https://github.com/PrimalHQ/primal-android-app/blob/ec22916b4e3472a20bade82d53957a7ef9b708b0/app/src/main/kotlin/net/primal/android/nostr/publish/NostrPublisher.kt#L20
 */
class NostrPublisherRepository(
    private val relaysSocketManager: at.torch.compose.network.relays.RelaysSocketManager,
    private val nostrNotary: NostrNotaryRepository,
    private val cachingImportRepository: CachingImportRepository,
) {
    val logger = Logger.withTag("NostrPublisherRepository")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun importEvent(event: at.torch.compose.database.model.NostrEvent) {
        scope.launch {
            runCatching {
                cachingImportRepository.importEvents(events = listOf(event))
            }.onFailure { error ->
                logger.w(throwable = error) { "Failed to import event ${event.id} to caching server." }
            }
        }
    }

    @Throws(at.torch.compose.exceptions.NostrPublishException::class)
    private suspend fun publishAndImportEvent(signedNostrEvent: at.torch.compose.database.model.NostrEvent, outboxRelays: List<String> = emptyList()) {
        relaysSocketManager.publishEvent(signedNostrEvent)
        importEvent(signedNostrEvent)
        if (outboxRelays.isNotEmpty()) {
            runCatching {
                relaysSocketManager.publishEvent(
                    nostrEvent = signedNostrEvent,
                    relays = outboxRelays.map {
                        _root_ide_package_.at.torch.compose.network.dto.RelayDTO(
                            url = it,
                            read = false,
                            write = true
                        )
                    }.toSet(),
                )
            }.onFailure { error ->
                logger.w(throwable = error) { "Failed to publish to outbox relays." }
            }
        }
    }

    @Throws(at.torch.compose.exceptions.NostrPublishException::class, at.torch.compose.exceptions.SignatureException::class)
    suspend fun signPublishImportNostrEvent(
        unsignedNostrEvent: at.torch.compose.database.model.UnsignedNostrEvent,
        outboxRelays: List<String>,
    ): at.torch.compose.database.model.NostrEvent {
        val signedNostrEvent = nostrNotary.signNostrEvent(unsignedNostrEvent = unsignedNostrEvent).unwrapOrThrow()
        publishAndImportEvent(signedNostrEvent = signedNostrEvent, outboxRelays = outboxRelays)
        return signedNostrEvent
    }


    @Throws(at.torch.compose.exceptions.NostrPublishException::class, at.torch.compose.exceptions.SignatureException::class)
    suspend fun publishRelayList(userId: String, relays: List<at.torch.compose.network.dto.RelayDTO>): at.torch.compose.database.model.NostrEvent {
        val signedNostrEvent = nostrNotary.signRelayListMetadata(userId = userId, relays = relays).unwrapOrThrow()
        relaysSocketManager.publishEvent(nostrEvent = signedNostrEvent, relays = relays.toSet())
        importEvent(signedNostrEvent)
        return signedNostrEvent
    }
}