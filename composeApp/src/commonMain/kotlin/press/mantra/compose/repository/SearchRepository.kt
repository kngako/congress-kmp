package press.mantra.compose.repository

interface SearchRepository {

    suspend fun submitSearch(
        synchronizeNostrEventRequests: List<press.mantra.compose.database.model.SynchronizeNostrEventRequest>,
    )

    companion object {
        /**
         * A search that reaches no relay: inert, and the one member does not throw. See
         * [NostrRepository.NO_OP_NOSTR_REPOSITORY] for why a `TODO()` here is a silent
         * misattribution rather than a loud failure.
         */
        val NO_OP_SEARCH_REPOSITORY: SearchRepository = object : SearchRepository {
            override suspend fun submitSearch(
                synchronizeNostrEventRequests: List<press.mantra.compose.database.model.SynchronizeNostrEventRequest>
            ) = Unit
        }
    }
}
