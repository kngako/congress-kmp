package ac.aux.compose.database.dao

import ac.aux.compose.database.model.SynchronizeNostrEventRequest
import ac.aux.compose.database.model.SynchronizeNostrEventResult
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SynchronizeNostrEventResultDao {
    @Upsert
    fun upsert(synchronizeNostrEventResult: SynchronizeNostrEventResult)
}