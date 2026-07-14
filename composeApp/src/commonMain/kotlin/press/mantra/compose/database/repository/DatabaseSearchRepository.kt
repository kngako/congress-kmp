package press.mantra.compose.database.repository

class DatabaseSearchRepository(
    private val database: press.mantra.compose.database.MantraDatabase
): press.mantra.compose.repository.SearchRepository {

    override suspend fun submitSearch(synchronizeNostrEventRequests: List<press.mantra.compose.database.model.SynchronizeNostrEventRequest>) {
        database.synchronizeNostrEventRequestDao().insert(
            synchronizeNostrEventRequests = synchronizeNostrEventRequests
        )
    }
}