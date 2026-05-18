package ac.cord.auxiliary.compose.database.dao

import ac.cord.auxiliary.compose.database.model.GiftWrapUnsigned
import ac.cord.auxiliary.compose.database.model.Relay
import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface GiftWrapUnsignedDao {
    @Query("SELECT * FROM GiftWrapUnsigned")
    fun  getAllGiftWrapUnsigned(): List<GiftWrapUnsigned>


    @Upsert
    suspend fun upsert(giftWrapUnsigned: GiftWrapUnsigned)

}