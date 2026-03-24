package ac.aux.compose.database.dao

import ac.aux.compose.database.model.NostrEvent
import ac.aux.compose.database.model.Post
import ac.aux.compose.database.model.Repost
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RepostDao {
    @Query("SELECT * FROM Repost")
    fun  getAllReposts(): List<Repost>

    @Query("SELECT * FROM Repost WHERE id = :id")
    fun getRepostById(id: String): Flow<Repost?>

    @Insert
    suspend fun insert(repost: Repost)
}