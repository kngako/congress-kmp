package ac.cord.auxiliary.compose.repository

import ac.cord.auxiliary.compose.database.model.SynchronizeNostrEventRequest

interface SearchRepository {

    suspend fun submitSearch(
        synchronizeNostrEventRequests: List<SynchronizeNostrEventRequest>,
    )

    companion object {
        val NO_OP_SEARCH_REPOSITORY = object: SearchRepository {
            override suspend fun submitSearch(synchronizeNostrEventRequests: List<SynchronizeNostrEventRequest>) {
                TODO("Not yet implemented")
            }
        }
    }
}