package ac.cord.auxiliary.compose.database.dao

import ac.cord.auxiliary.compose.database.model.GiftWrapSeal
import ac.cord.auxiliary.compose.database.model.Relay
import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface GiftWrapSealDao {
    @Query("SELECT * FROM GiftWrapSeal")
    fun  getAllGiftWrapSeals(): List<GiftWrapSeal>

    @Upsert
    suspend fun upsert(giftWrapSeal: GiftWrapSeal)

}