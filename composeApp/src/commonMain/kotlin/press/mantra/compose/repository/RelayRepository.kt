package press.mantra.compose.repository

import kotlinx.coroutines.flow.Flow

interface RelayRepository {
    suspend fun observePublicKeyRelays(publicKey: String): Flow<List<press.mantra.compose.database.model.Relay>>
}