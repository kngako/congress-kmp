package ac.cord.auxiliary.compose.database.dao

import ac.cord.auxiliary.compose.database.model.BroadcastNostrEventReceipt
import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BroadcastNostrEventReceiptDao {
    @Query("SELECT * FROM BroadcastNostrEventReceipt")
    fun  getAllBroadcastNostrEventReceipts(): List<BroadcastNostrEventReceipt>

    @Query("SELECT * FROM BroadcastNostrEventReceipt WHERE id = :id")
    fun getBroadcastNostrEventReceiptById(id: Long): Flow<BroadcastNostrEventReceipt?>

    @Query("SELECT * FROM BroadcastNostrEventReceipt WHERE nostrEventId = :nostrEventId")
    fun getBroadcastNostrEventReceiptByNostrEventId(nostrEventId: String): Flow<BroadcastNostrEventReceipt?>

    @Upsert
    suspend fun upsert(broadcastNostrEventReceipt: BroadcastNostrEventReceipt)
}