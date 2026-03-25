package ac.aux.compose.repository

import ac.aux.compose.database.model.UnsignedNostrEvent

interface NostrRepository {
    suspend fun saveUnsignedNostrEvent(unsignedNostrEvent: UnsignedNostrEvent): UnsignedNostrEvent?
}