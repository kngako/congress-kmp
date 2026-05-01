package ac.cord.auxiliary.compose.database.dao

import ac.cord.auxiliary.compose.database.model.UnsignedNostrEvent
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalAccount
import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface UnsignedNostrEventDao {
    @Query("SELECT * FROM UnsignedNostrEvent")
    fun  getAUnsignedNostrEvents(): List<UnsignedNostrEvent>

    @Query("SELECT * FROM UnsignedNostrEvent") // TODO: where nostrEvent.unsignedNostrEventId is 0
    fun  getAllUnprocessedUnsignedNostrEvents(): List<UnsignedNostrEvent>

    @Query("SELECT * FROM UnsignedNostrEvent WHERE id = :id")
    fun getUnsignedNostrEventById(id: Long): Flow<UnsignedNostrEvent?>

    @Upsert
    suspend fun upsert(unsignedNostrEvent: UnsignedNostrEvent): Long

    @Query("SELECT * FROM UnsignedNostrEvent WHERE kind = 0 AND pubKey = :publicKey")
    fun observeProfile(publicKey: String): Flow<LocalAccount?>

    @Query("SELECT * FROM UnsignedNostrEvent WHERE pubKey = :publicKey AND signedAt IS NULL") // TODO: where nostrEvent.unsignedNostrEventId is 0
    fun observeUnsignedNostrEvents(publicKey: String): Flow<UnsignedNostrEvent?>
}