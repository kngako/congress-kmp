package ac.cord.auxiliary.compose.database.dao

import ac.cord.auxiliary.compose.database.model.Repost
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RepostDao {
    @Query("SELECT * FROM Repost")
    fun  getAllReposts(): List<Repost>

    @Query("SELECT * FROM Repost WHERE id = :id")
    fun getRepostById(id: String): Flow<Repost?>

    @Upsert
    suspend fun upsert(repost: Repost)
}