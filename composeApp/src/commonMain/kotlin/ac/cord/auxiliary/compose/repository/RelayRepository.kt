package ac.cord.auxiliary.compose.repository

import ac.cord.auxiliary.compose.database.model.Relay
import kotlinx.coroutines.flow.Flow

interface RelayRepository {
    suspend fun observePublicKeyRelays(publicKey: String): Flow<List<Relay>>
}