package ac.aux.compose.database.dao

import ac.aux.compose.database.model.NostrEvent
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy.Companion.IGNORE
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface NostrEventDao {
    @Query("SELECT * FROM NostrEvent ORDER BY createdAt DESC")
    fun  getAllNostrEvents(): List<NostrEvent>

    @Query("SELECT * FROM NostrEvent WHERE id = :id")
    fun getNostrEventById(id: String): Flow<NostrEvent?>

    @Upsert
    suspend fun upsert(nostrEvent: NostrEvent)

    @Insert(
        onConflict = IGNORE
    )
    suspend fun insert(nostrEvent: NostrEvent)
}