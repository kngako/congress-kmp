package ac.cord.auxiliary.compose.database.dao

import ac.cord.auxiliary.compose.database.model.Reaction
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ReactionDao {
    @Query("SELECT * FROM Reaction")
    fun  getAllReactions(): List<Reaction>

    @Query("SELECT * FROM Reaction WHERE id = :id")
    fun getReactionById(id: String): Flow<Reaction?>

    @Upsert
    suspend fun upsert(reaction: Reaction)
}