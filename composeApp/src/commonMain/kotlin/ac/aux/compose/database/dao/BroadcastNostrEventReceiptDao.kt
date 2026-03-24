package ac.aux.compose.database.dao

import ac.aux.compose.database.model.BroadcastNostrEventReceipt
import ac.aux.compose.database.model.BroadcastNostrEventRequest
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
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