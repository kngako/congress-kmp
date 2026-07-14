package press.mantra.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock
import kotlin.time.Instant

@Dao
interface SynchronizeNostrEventRequestDao {
    @Query("SELECT * FROM SynchronizeNostrEventRequest WHERE status = :status AND createdAt > :createdAt")
    fun observeSynchronizeNostrEventRequestsByStatus(status: String, createdAt: Instant = Clock.System.now()): Flow<press.mantra.compose.database.model.SynchronizeNostrEventRequest?>

    @Query("SELECT COUNT(*) FROM SynchronizeNostrEventRequest WHERE purpose = :purpose AND status IN (:status) AND createdAt > :createdAt")
    fun observeSynchronizeNostrEventRequestByPurposeAndStatusCount(purpose: String, status: List<String>, createdAt: Instant = Clock.System.now()): Flow<Int>

    @Upsert
    fun upsert(synchronizeNostrEventRequest: press.mantra.compose.database.model.SynchronizeNostrEventRequest)

    @Insert
    fun insert(synchronizeNostrEventRequests: List<press.mantra.compose.database.model.SynchronizeNostrEventRequest>)
}