package at.torch.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Upsert
import at.torch.compose.database.model.BroadcastNostrEventRequest
import at.torch.compose.database.model.intermdiate.LocalBroadcastNostrEventRequest
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock
import kotlin.time.Instant

@Dao
interface BroadcastNostrEventRequestDao {
    @Query("SELECT * FROM BroadcastNostrEventRequest")
    fun  getAllBroadcastNostrEventRequests(): List<BroadcastNostrEventRequest>

    @Query("SELECT * FROM BroadcastNostrEventRequest WHERE nostrEventId = :nostrEventId ORDER BY createdAt ASC")
    fun  getFirstBroadcastNostrEventRequestByNostrEventId(nostrEventId: String): BroadcastNostrEventRequest?

    @Query("SELECT * FROM BroadcastNostrEventRequest WHERE status = :status AND createdAt > :createdAt")
    fun observeBroadcastNostrEventRequestsByStatus(status: String, createdAt: Instant = Clock.System.now()): Flow<LocalBroadcastNostrEventRequest?>

    @Query("SELECT * FROM BroadcastNostrEventRequest WHERE nostrEventId = :nostrEventId")
    fun observeBroadcastNostrEventRequestByNostrEventId(nostrEventId: String): Flow<BroadcastNostrEventRequest?>

    @Upsert
    suspend fun upsert(broadcastNostrEventRequest: BroadcastNostrEventRequest): Long

    @Insert
    suspend fun insert(broadcastNostrEventRequests: List<BroadcastNostrEventRequest>): LongArray
}