package ac.cord.auxiliary.compose.database.dao

import ac.cord.auxiliary.compose.database.model.BroadcastNostrEventRequest
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalBroadcastNostrEventRequest
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
    fun  getAllBroadcastNostrEventRequests(): List<BroadcastNostrEventRequest>

    @Query("SELECT * FROM BroadcastNostrEventRequest WHERE status = :status AND createdAt > :createdAt")
    fun observeBroadcastNostrEventRequestsByStatus(status: String, createdAt: Instant = Clock.System.now()): Flow<List<LocalBroadcastNostrEventRequest>>

    @Query("SELECT * FROM BroadcastNostrEventRequest WHERE nostrEventId = :nostrEventId")
    fun observeBroadcastNostrEventRequestByNostrEventId(nostrEventId: String): Flow<BroadcastNostrEventRequest?>

    @Upsert
    suspend fun upsert(broadcastNostrEventRequest: BroadcastNostrEventRequest)

    @Insert
    suspend fun insert(broadcastNostrEventRequests: List<BroadcastNostrEventRequest>)
}