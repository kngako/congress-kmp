package ac.cord.auxiliary.compose.database.dao

import ac.cord.auxiliary.compose.database.model.Zap
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ZapDao {
    @Query("SELECT * FROM Zap WHERE id = :id")
    fun getZapById(id: String): Flow<Zap?>

    @Upsert
    suspend fun upsert(zap: Zap)
}