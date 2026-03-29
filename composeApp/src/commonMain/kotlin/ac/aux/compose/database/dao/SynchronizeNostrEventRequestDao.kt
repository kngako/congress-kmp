package ac.aux.compose.database.dao

import ac.aux.compose.database.model.SynchronizeNostrEventRequest
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SynchronizeNostrEventRequestDao {
    @Query("SELECT * FROM SynchronizeNostrEventRequest WHERE status = :status")
    fun observeSynchronizeNostrEventRequestsByStatus(status: String): Flow<SynchronizeNostrEventRequest?>

    @Upsert
    fun upsert(synchronizeNostrEventRequest: SynchronizeNostrEventRequest)
}