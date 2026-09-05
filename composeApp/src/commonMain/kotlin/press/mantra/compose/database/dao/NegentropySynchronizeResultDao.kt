package press.mantra.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Upsert

@Dao
interface NegentropySynchronizeResultDao {
    @Upsert
    suspend fun upsert(negentropySynchronizeResult: press.mantra.compose.database.model.NegentropySynchronizeResult)
}