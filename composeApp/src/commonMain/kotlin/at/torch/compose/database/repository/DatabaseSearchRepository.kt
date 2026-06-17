package at.torch.compose.database.repository

class DatabaseSearchRepository(
    private val database: at.torch.compose.database.TorchDatabase
): at.torch.compose.repository.SearchRepository {

    override suspend fun submitSearch(synchronizeNostrEventRequests: List<at.torch.compose.database.model.SynchronizeNostrEventRequest>) {
        database.synchronizeNostrEventRequestDao().insert(
            synchronizeNostrEventRequests = synchronizeNostrEventRequests
        )
    }
}