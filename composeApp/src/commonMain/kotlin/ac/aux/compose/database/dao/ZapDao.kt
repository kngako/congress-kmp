package ac.aux.compose.database.dao

import ac.aux.compose.database.model.Zap
import androidx.room.Dao
import androidx.room.Insert
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