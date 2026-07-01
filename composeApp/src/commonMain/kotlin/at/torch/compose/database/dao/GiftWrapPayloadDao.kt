package at.torch.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface GiftWrapPayloadDao {
    @Query("SELECT * FROM GiftWrapPayload")
    fun  getAllGiftWrapUnsigned(): List<at.torch.compose.database.model.GiftWrapPayload>

    @Query("SELECT * FROM GiftWrapPayload WHERE publicKey = :publicKey AND giftWrapSealId IS NULL")
    fun observeUnsealedGiftWrapPayloads(publicKey: String): Flow<at.torch.compose.database.model.GiftWrapPayload?>

    @Upsert
    suspend fun upsert(giftWrapPayload: at.torch.compose.database.model.GiftWrapPayload)
}