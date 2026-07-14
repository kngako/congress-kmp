package press.mantra.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Upsert
import press.mantra.compose.database.model.GiftWrapPayload
import kotlinx.coroutines.flow.Flow

@Dao
interface GiftWrapPayloadDao {
    @Query("SELECT * FROM GiftWrapPayload")
    fun  getAllGiftWrapUnsigned(): List<GiftWrapPayload>

    @Query("SELECT * FROM GiftWrapPayload WHERE publicKey = :publicKey AND giftWrapSealId IS NULL")
    fun observeUnsealedGiftWrapPayloads(publicKey: String): Flow<GiftWrapPayload?>

    @Upsert
    suspend fun upsert(giftWrapPayload: GiftWrapPayload)

    @Insert
    suspend fun insert(giftWrapPayload: GiftWrapPayload)
}