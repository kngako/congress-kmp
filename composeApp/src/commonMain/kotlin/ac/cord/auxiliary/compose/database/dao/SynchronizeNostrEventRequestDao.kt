package ac.cord.auxiliary.compose.database.dao

import ac.cord.auxiliary.compose.database.model.SynchronizeNostrEventRequest
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock
import kotlin.time.Instant

@Dao
interface SynchronizeNostrEventRequestDao {
    @Query("SELECT * FROM SynchronizeNostrEventRequest WHERE status = :status AND createdAt > :createdAt")
    fun observeSynchronizeNostrEventRequestsByStatus(status: String, createdAt: Instant = Clock.System.now()): Flow<SynchronizeNostrEventRequest?>

    @Query("SELECT COUNT(*) FROM SynchronizeNostrEventRequest WHERE purpose = :purpose AND status IN (:status) AND createdAt > :createdAt")
    fun observeSynchronizeNostrEventRequestByPurposeAndStatusCount(purpose: String, status: List<String>, createdAt: Instant = Clock.System.now()): Flow<Int>

    @Upsert
    fun upsert(synchronizeNostrEventRequest: SynchronizeNostrEventRequest)

    @Insert
    fun insert(synchronizeNostrEventRequests: List<SynchronizeNostrEventRequest>)
}