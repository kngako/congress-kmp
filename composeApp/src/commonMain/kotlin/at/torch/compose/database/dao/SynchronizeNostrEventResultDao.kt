package at.torch.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Upsert

@Dao
interface SynchronizeNostrEventResultDao {
    @Upsert
    fun upsert(synchronizeNostrEventResult: at.torch.compose.database.model.SynchronizeNostrEventResult)
}