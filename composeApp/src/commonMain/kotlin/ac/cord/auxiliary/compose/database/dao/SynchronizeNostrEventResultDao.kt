package ac.cord.auxiliary.compose.database.dao

import ac.cord.auxiliary.compose.database.model.SynchronizeNostrEventResult
import androidx.room3.Dao
import androidx.room3.Upsert

@Dao
interface SynchronizeNostrEventResultDao {
    @Upsert
    fun upsert(synchronizeNostrEventResult: SynchronizeNostrEventResult)
}