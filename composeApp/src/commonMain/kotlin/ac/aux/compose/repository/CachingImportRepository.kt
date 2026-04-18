package ac.aux.compose.repository

import ac.aux.compose.database.model.NostrEvent
import ac.aux.compose.network.relays.broadcast.BroadcastEventResponse

interface CachingImportRepository {
    suspend fun cacheNostrEvents(vararg events: NostrEvent)
    suspend fun cacheNostrEvents(events: List<NostrEvent>)

    suspend fun importEvents(events: List<NostrEvent>): Boolean
    suspend fun broadcastEvents(events: List<NostrEvent>, relays: List<String>): Result<List<BroadcastEventResponse>>
}
