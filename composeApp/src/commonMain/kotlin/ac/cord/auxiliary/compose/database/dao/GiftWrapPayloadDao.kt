package ac.cord.auxiliary.compose.database.dao

import ac.cord.auxiliary.compose.database.model.GiftWrapPayload
import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert

@Dao
interface GiftWrapPayloadDao {
    @Query("SELECT * FROM GiftWrapPayload")
    fun  getAllGiftWrapUnsigned(): List<GiftWrapPayload>

    @Upsert
    suspend fun upsert(giftWrapPayload: GiftWrapPayload)

}