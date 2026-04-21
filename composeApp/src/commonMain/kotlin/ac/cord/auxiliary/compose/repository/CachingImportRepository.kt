package ac.cord.auxiliary.compose.repository

import ac.cord.auxiliary.compose.database.model.NostrEvent
import ac.cord.auxiliary.compose.network.relays.broadcast.BroadcastEventResponse

/**
 * As seen in Primal
 *
 * https://github.com/PrimalHQ/primal-android-app/blob/a7e32e2203555d27e60a749238175cd1ee0fa2af/domain/primal/src/commonMain/kotlin/net/primal/domain/global/CachingImportRepository.kt
 *
 */
interface CachingImportRepository {
    suspend fun cacheNostrEvents(vararg events: NostrEvent)
    suspend fun cacheNostrEvents(events: List<NostrEvent>)

    suspend fun importEvents(events: List<NostrEvent>): Boolean
    suspend fun broadcastEvents(events: List<NostrEvent>, relays: List<String>): Result<List<BroadcastEventResponse>>

    companion object {
        val NO_OP_CACHING_IMPORT_REPOSITORY = object : CachingImportRepository {
            override suspend fun cacheNostrEvents(vararg events: NostrEvent) {
            }

            override suspend fun cacheNostrEvents(events: List<NostrEvent>) {

            }

            override suspend fun importEvents(events: List<NostrEvent>): Boolean {
                return true
            }

            override suspend fun broadcastEvents(
                events: List<NostrEvent>,
                relays: List<String>
            ): Result<List<BroadcastEventResponse>> {
                return Result.success(
                    emptyList()
                )
            }

        }
    }
}
