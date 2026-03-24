package ac.aux.compose.database.dao

import ac.aux.compose.database.model.NostrEvent
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NostrEventDao {
    @Query("SELECT * FROM NostrEvent")
    fun  getAllNostrEvents(): List<NostrEvent>

    @Query("SELECT * FROM NostrEvent WHERE id = :id")
    fun getNostrEventById(id: String): Flow<NostrEvent?>

    @Insert
    suspend fun insert(nostrEvent: NostrEvent)
}