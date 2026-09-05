package press.mantra.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Upsert

@Dao
interface SynchronizeNostrEventResultDao {
    @Upsert
    suspend fun upsert(synchronizeNostrEventResult: press.mantra.compose.database.model.SynchronizeNostrEventResult)
}