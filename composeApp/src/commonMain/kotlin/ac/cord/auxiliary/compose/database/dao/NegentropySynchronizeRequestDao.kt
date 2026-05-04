package ac.cord.auxiliary.compose.database.dao

import ac.cord.auxiliary.compose.database.model.NegentropySynchronizeRequest
import ac.cord.auxiliary.compose.database.model.SynchronizeNostrEventRequest
import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy.Companion.IGNORE
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock
import kotlin.time.Instant

@Dao
interface NegentropySynchronizeRequestDao {
    @Query("SELECT * FROM NegentropySynchronizeRequest WHERE status = :status AND createdAt > :createdAt")
    fun observeNegentropySynchronizeRequestsByStatus(status: String, createdAt: Instant = Clock.System.now()): Flow<NegentropySynchronizeRequest?>

    @Query("SELECT COUNT(*) FROM NegentropySynchronizeRequest WHERE purpose = :purpose AND status IN (:status) AND createdAt > :createdAt")
    fun observeNegentropySynchronizeRequestByPurposeAndStatusCount(purpose: String, status: List<String>, createdAt: Instant = Clock.System.now()): Flow<Int>

    @Upsert
    fun upsert(negentropySynchronizeRequest: NegentropySynchronizeRequest)

    @Insert(
        onConflict = IGNORE
    )
    fun insert(negentropySynchronizeRequests: List<NegentropySynchronizeRequest>)
}