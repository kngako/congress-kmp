package ac.cord.auxiliary.compose.database.dao

import ac.cord.auxiliary.compose.database.model.SynchronizeNostrEventResult
import androidx.room.Dao
import androidx.room.Upsert

@Dao
interface SynchronizeNostrEventResultDao {
    @Upsert
    fun upsert(synchronizeNostrEventResult: SynchronizeNostrEventResult)
}