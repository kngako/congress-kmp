package ac.cord.auxiliary.compose.database.dao

import ac.cord.auxiliary.compose.database.model.Relay
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RelayDao {
    @Query("SELECT * FROM Relay")
    fun  getAllPosts(): List<Relay>

    @Query("SELECT * FROM Relay WHERE publicKey = :publicKey")
    fun observePublicKeyRelays(publicKey: String): Flow<List<Relay>>

    @Upsert
    suspend fun upsert(relay: Relay)

}