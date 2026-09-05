package press.mantra.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Upsert
import press.mantra.compose.database.model.GiftWrapPayload
import com.vitorpamplona.quartz.nip01Core.core.Kind
import kotlinx.coroutines.flow.Flow

@Dao
interface GiftWrapPayloadDao {
    @Query("SELECT * FROM GiftWrapPayload")
    suspend fun  getAllGiftWrapUnsigned(): List<GiftWrapPayload>

    @Query("SELECT * FROM GiftWrapPayload WHERE publicKey = :publicKey AND giftWrapSealId IS NULL")
    fun observeUnsealedGiftWrapPayloads(publicKey: String): Flow<GiftWrapPayload?>

    /**
     * Every payload of the given kinds this device holds for a room, oldest first.
     *
     * Lets a handler read back messages it could not act on when they arrived.
     * Payloads are stored before the inbound path dispatches on kind, so one that
     * turned up too early — a ChillDKG round message ahead of the proposal that
     * opens the ritual — is here waiting rather than lost.
     */
    @Query(
        "SELECT * FROM GiftWrapPayload " +
            "WHERE chatRoomId = :chatRoomId AND kind IN (:kinds) " +
            "ORDER BY createdAt ASC"
    )
    suspend fun getByChatRoomAndKinds(chatRoomId: String, kinds: List<Kind>): List<GiftWrapPayload>

    @Upsert
    suspend fun upsert(giftWrapPayload: GiftWrapPayload)

    @Insert
    suspend fun insert(giftWrapPayload: GiftWrapPayload)
}