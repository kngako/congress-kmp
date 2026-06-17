package at.torch.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Upsert

@Dao
interface NegentropySynchronizeResultDao {
    @Upsert
    fun upsert(negentropySynchronizeResult: at.torch.compose.database.model.NegentropySynchronizeResult)
}