package ac.aux.compose.database.dao

import ac.aux.compose.database.model.BroadcastNostrEventRequest
import ac.aux.compose.database.model.intermdiate.LocalBroadcastNostrEventRequest
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BroadcastNostrEventRequestDao {
    @Query("SELECT * FROM BroadcastNostrEventRequest")
    fun  getAllBroadcastNostrEventRequests(): List<BroadcastNostrEventRequest>

    @Query("SELECT * FROM BroadcastNostrEventRequest WHERE status = :status")
    fun observeBroadcastNostrEventRequestsByStatus(status: String): Flow<List<LocalBroadcastNostrEventRequest>>

    @Query("SELECT * FROM BroadcastNostrEventRequest WHERE nostrEventId = :nostrEventId")
    fun observeBroadcastNostrEventRequestByNostrEventId(nostrEventId: String): Flow<BroadcastNostrEventRequest?>

    @Upsert
    suspend fun upsert(broadcastNostrEventRequest: BroadcastNostrEventRequest)
}