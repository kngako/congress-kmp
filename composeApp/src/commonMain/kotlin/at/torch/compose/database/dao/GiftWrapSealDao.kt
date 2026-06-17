package at.torch.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert

@Dao
interface GiftWrapSealDao {
    @Query("SELECT * FROM GiftWrapSeal")
    fun  getAllGiftWrapSeals(): List<at.torch.compose.database.model.GiftWrapSeal>

    @Upsert
    suspend fun upsert(giftWrapSeal: at.torch.compose.database.model.GiftWrapSeal)

}