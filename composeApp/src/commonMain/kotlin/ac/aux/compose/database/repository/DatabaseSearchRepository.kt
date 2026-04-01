package ac.aux.compose.database.repository

import ac.aux.compose.database.AuxDatabase
import ac.aux.compose.database.model.SynchronizeNostrEventRequest
import ac.aux.compose.repository.SearchRepository

class DatabaseSearchRepository(
    private val database: AuxDatabase
): SearchRepository {

    override suspend fun submitSearch(synchronizeNostrEventRequests: List<SynchronizeNostrEventRequest>) {
        database.synchronizeNostrEventRequestDao().insert(
            synchronizeNostrEventRequests = synchronizeNostrEventRequests
        )
    }
}