package at.torch.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface UnsignedNostrEventDao {
    @Query("SELECT * FROM UnsignedNostrEvent")
    fun  getAUnsignedNostrEvents(): List<at.torch.compose.database.model.UnsignedNostrEvent>

    @Query("SELECT * FROM UnsignedNostrEvent") // TODO: where nostrEvent.unsignedNostrEventId is 0
    fun  getAllUnprocessedUnsignedNostrEvents(): List<at.torch.compose.database.model.UnsignedNostrEvent>

    @Query("SELECT * FROM UnsignedNostrEvent WHERE id = :id")
    fun getUnsignedNostrEventById(id: Long): Flow<at.torch.compose.database.model.UnsignedNostrEvent?>

    @Upsert
    suspend fun upsert(unsignedNostrEvent: at.torch.compose.database.model.UnsignedNostrEvent): Long

    @Insert
    suspend fun insert(unsignedNostrEvents: List<at.torch.compose.database.model.UnsignedNostrEvent>)

    @Transaction
    @Query("SELECT * FROM UnsignedNostrEvent WHERE kind = 0 AND pubKey = :publicKey")
    fun observeProfile(publicKey: String): Flow<at.torch.compose.database.model.intermdiate.LocalAccount?>

    @Query("SELECT * FROM UnsignedNostrEvent WHERE pubKey = :publicKey AND signedAt IS NULL")
    fun observeUnsignedNostrEvents(publicKey: String): Flow<at.torch.compose.database.model.UnsignedNostrEvent?>
}