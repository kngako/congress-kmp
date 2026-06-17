package at.torch.compose.repository

interface SearchRepository {

    suspend fun submitSearch(
        synchronizeNostrEventRequests: List<at.torch.compose.database.model.SynchronizeNostrEventRequest>,
    )

    companion object {
        val NO_OP_SEARCH_REPOSITORY = object: SearchRepository {
            override suspend fun submitSearch(synchronizeNostrEventRequests: List<at.torch.compose.database.model.SynchronizeNostrEventRequest>) {
                TODO("Not yet implemented")
            }
        }
    }
}