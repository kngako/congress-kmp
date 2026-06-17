package at.torch.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock
import kotlin.time.Instant

@Dao
interface BroadcastNostrEventRequestDao {
    @Query("SELECT * FROM BroadcastNostrEventRequest")
    fun  getAllBroadcastNostrEventRequests(): List<at.torch.compose.database.model.BroadcastNostrEventRequest>

    @Query("SELECT * FROM BroadcastNostrEventRequest WHERE status = :status AND createdAt > :createdAt")
    fun observeBroadcastNostrEventRequestsByStatus(status: String, createdAt: Instant = Clock.System.now()): Flow<List<at.torch.compose.database.model.intermdiate.LocalBroadcastNostrEventRequest>>

    @Query("SELECT * FROM BroadcastNostrEventRequest WHERE nostrEventId = :nostrEventId")
    fun observeBroadcastNostrEventRequestByNostrEventId(nostrEventId: String): Flow<at.torch.compose.database.model.BroadcastNostrEventRequest?>

    @Upsert
    suspend fun upsert(broadcastNostrEventRequest: at.torch.compose.database.model.BroadcastNostrEventRequest)

    @Insert
    suspend fun insert(broadcastNostrEventRequests: List<at.torch.compose.database.model.BroadcastNostrEventRequest>): LongArray
}