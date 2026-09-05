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

    /**
     * A room's inner events of the given kinds, oldest first.
     *
     * Used to replay a protocol backlog: a session's messages are stored as they
     * decrypt, but one that arrives before the proposal opening its session has
     * nowhere to be filed at the time.
     */
    @Query("SELECT * FROM MarmotInnerEvent WHERE chatRoomId = :chatRoomId AND kind IN (:kinds) ORDER BY createdAt ASC")
    suspend fun getByChatRoomAndKinds(chatRoomId: String, kinds: List<Int>): List<MarmotInnerEvent>

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
}