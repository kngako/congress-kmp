package at.torch.compose.repository

import kotlinx.coroutines.flow.Flow

interface RelayRepository {
    suspend fun observePublicKeyRelays(publicKey: String): Flow<List<at.torch.compose.database.model.Relay>>
}