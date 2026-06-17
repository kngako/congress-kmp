package at.torch.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert

@Dao
interface GiftWrapMessageDao {
    @Query("SELECT * FROM GiftWrapMessage")
    fun  getAllGiftWrapMessages(): List<at.torch.compose.database.model.GiftWrapMessage>

    @Upsert
    suspend fun upsert(giftWrapMessage: at.torch.compose.database.model.GiftWrapMessage)

}