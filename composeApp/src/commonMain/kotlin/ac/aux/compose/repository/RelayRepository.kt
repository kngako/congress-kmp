package ac.aux.compose.repository

import ac.aux.compose.database.model.Relay
import kotlinx.coroutines.flow.Flow

interface RelayRepository {
    suspend fun observePublicKeyRelays(publicKey: String): Flow<List<Relay>>
}