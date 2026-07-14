package press.mantra.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert

@Dao
interface GiftWrapSealDao {
    @Query("SELECT * FROM GiftWrapSeal")
    fun  getAllGiftWrapSeals(): List<press.mantra.compose.database.model.GiftWrapSeal>

    @Upsert
    suspend fun upsert(giftWrapSeal: press.mantra.compose.database.model.GiftWrapSeal)

}