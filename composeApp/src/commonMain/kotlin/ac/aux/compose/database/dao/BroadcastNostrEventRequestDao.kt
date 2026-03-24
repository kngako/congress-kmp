package ac.aux.compose.database.dao

import ac.aux.compose.database.model.BroadcastNostrEventRequest
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BroadcastNostrEventRequestDao {
    @Query("SELECT * FROM BroadcastNostrEventRequest")
    fun  getAllBroadcastNostrEventRequests(): List<BroadcastNostrEventRequest>

    @Query("SELECT * FROM BroadcastNostrEventRequest WHERE id = :id")
    fun getBroadcastNostrEventRequestById(id: Long): Flow<BroadcastNostrEventRequest?>

    @Query("SELECT * FROM BroadcastNostrEventRequest WHERE nostrEventId = :nostrEventId")
    fun getBroadcastNostrEventRequestByNostrEventId(nostrEventId: String): Flow<BroadcastNostrEventRequest?>

    @Upsert
    suspend fun upsert(broadcastNostrEventRequest: BroadcastNostrEventRequest)
}