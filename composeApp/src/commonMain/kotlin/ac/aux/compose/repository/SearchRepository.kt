package ac.aux.compose.repository

import ac.aux.compose.database.model.Profile
import ac.aux.compose.database.model.SynchronizeNostrEventRequest
import ac.aux.compose.database.model.intermdiate.LocalProfile

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