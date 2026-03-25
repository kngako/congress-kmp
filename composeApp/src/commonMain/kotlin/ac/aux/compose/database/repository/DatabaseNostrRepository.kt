package ac.aux.compose.database.repository

import ac.aux.compose.database.AuxDatabase
import ac.aux.compose.database.model.UnsignedNostrEvent
import ac.aux.compose.repository.NostrRepository
import kotlinx.coroutines.delay

class DatabaseNostrRepository(
    private val database: AuxDatabase
): NostrRepository {

    override suspend fun saveUnsignedNostrEvent(unsignedNostrEvent: UnsignedNostrEvent): UnsignedNostrEvent? = try {
        delay(2_100) // Just to make it look like we are doing serious work...
        unsignedNostrEvent.copy(
            id = database.unsignedNostrEventDao().upsert(unsignedNostrEvent)
        )
    } catch (e: Throwable) {
        null
    }

}