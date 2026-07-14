package press.mantra.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import press.mantra.compose.database.model.intermdiate.LocalAccount
import kotlinx.coroutines.flow.Flow

@Dao
interface UnsignedNostrEventDao {
    @Query("SELECT * FROM UnsignedNostrEvent")
    fun  getAUnsignedNostrEvents(): List<press.mantra.compose.database.model.UnsignedNostrEvent>

    @Query("SELECT * FROM UnsignedNostrEvent") // TODO: where nostrEvent.unsignedNostrEventId is 0
    fun  getAllUnprocessedUnsignedNostrEvents(): List<press.mantra.compose.database.model.UnsignedNostrEvent>

    @Query("SELECT * FROM UnsignedNostrEvent WHERE id = :id")
    fun getUnsignedNostrEventById(id: Long): Flow<press.mantra.compose.database.model.UnsignedNostrEvent?>

    @Upsert
    suspend fun upsert(unsignedNostrEvent: press.mantra.compose.database.model.UnsignedNostrEvent): Long

    @Insert
    suspend fun insert(unsignedNostrEvents: List<press.mantra.compose.database.model.UnsignedNostrEvent>)

    @Transaction
    @Query("SELECT * FROM UnsignedNostrEvent WHERE kind = 0 AND pubKey = :publicKey")
    fun observeProfile(publicKey: String): Flow<LocalAccount?>

    @Transaction
    @Query("SELECT * FROM UnsignedNostrEvent WHERE kind = 0")
    fun getLocalAccounts(): List<LocalAccount>

    @Query("SELECT * FROM UnsignedNostrEvent WHERE pubKey = :publicKey AND signedAt IS NULL")
    fun observeUnsignedNostrEvents(publicKey: String): Flow<press.mantra.compose.database.model.UnsignedNostrEvent?>
}