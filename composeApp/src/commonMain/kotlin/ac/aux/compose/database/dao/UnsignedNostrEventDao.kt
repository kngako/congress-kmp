package ac.aux.compose.database.dao

import ac.aux.compose.database.model.NostrEvent
import ac.aux.compose.database.model.UnsignedNostrEvent
import ac.aux.compose.database.model.intermdiate.LocalProfile
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface UnsignedNostrEventDao {
    @Query("SELECT * FROM UnsignedNostrEvent")
    fun  getAUnsignedNostrEvents(): List<UnsignedNostrEvent>

    @Query("SELECT * FROM UnsignedNostrEvent WHERE signedNostrEventId IS NULL")
    fun  getAllUnprocessedUnsignedNostrEvents(): List<UnsignedNostrEvent>

    @Query("SELECT * FROM UnsignedNostrEvent WHERE id = :id")
    fun getUnsignedNostrEventById(id: Long): Flow<UnsignedNostrEvent?>

    @Upsert
    suspend fun upsert(unsignedNostrEvent: UnsignedNostrEvent): Long

    @Query("SELECT * FROM UnsignedNostrEvent WHERE kind = 0 AND pubKey = :publicKey")
    fun observeProfile(publicKey: String): Flow<LocalProfile?>

    @Query("SELECT * FROM UnsignedNostrEvent WHERE pubKey = :publicKey AND signedNostrEventId IS NULL")
    fun observeUnsignedNostrEvents(publicKey: String): Flow<UnsignedNostrEvent>
}