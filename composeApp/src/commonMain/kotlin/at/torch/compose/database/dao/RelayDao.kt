package at.torch.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RelayDao {
    @Query("SELECT * FROM Relay")
    fun  getAllPosts(): List<at.torch.compose.database.model.Relay>

    @Query("SELECT * FROM Relay WHERE publicKey = :publicKey")
    fun observePublicKeyRelays(publicKey: String): Flow<List<at.torch.compose.database.model.Relay>>

    @Upsert
    suspend fun upsert(relay: at.torch.compose.database.model.Relay)

}