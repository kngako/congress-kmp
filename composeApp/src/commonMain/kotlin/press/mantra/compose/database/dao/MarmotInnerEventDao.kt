package press.mantra.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import press.mantra.compose.database.model.MarmotInnerEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface MarmotInnerEventDao {

    @Query("SELECT * FROM MarmotInnerEvent WHERE publicKey = :publicKey AND marmotGroupEventId IS NULL ORDER BY createdAt ASC")
    fun observeUnprocessedMarmotInnerEvents(publicKey: String): Flow<MarmotInnerEvent?>

    @Upsert
    suspend fun upsert(marmotInnerEvent: MarmotInnerEvent)

    @Query("DELETE FROM MarmotInnerEvent WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Drop the submissions carrying [payloadEventId].
     *
     * A submission's id is the envelope's, not the payload's, so a superseded
     * nip30303 event cannot be un-queued by its own id.
     */
    @Query("DELETE FROM MarmotInnerEvent WHERE payloadEventId = :payloadEventId")
    suspend fun deleteByPayloadEventId(payloadEventId: String)

    /**
     * The submissions held back waiting for [eventId], oldest first.
     *
     * Oldest first because a backlog usually arrives in the order it was
     * written, so applying it that way unblocks the most in one pass.
     */
    @Query("SELECT * FROM MarmotInnerEvent WHERE awaitingEventId = :eventId ORDER BY createdAt ASC")
    suspend fun getSubmissionsAwaiting(eventId: String): List<MarmotInnerEvent>
}