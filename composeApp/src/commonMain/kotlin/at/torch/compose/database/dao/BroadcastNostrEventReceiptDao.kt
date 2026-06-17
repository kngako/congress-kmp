package at.torch.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BroadcastNostrEventReceiptDao {
    @Query("SELECT * FROM BroadcastNostrEventReceipt")
    fun  getAllBroadcastNostrEventReceipts(): List<at.torch.compose.database.model.BroadcastNostrEventReceipt>

    @Query("SELECT * FROM BroadcastNostrEventReceipt WHERE id = :id")
    fun getBroadcastNostrEventReceiptById(id: Long): Flow<at.torch.compose.database.model.BroadcastNostrEventReceipt?>

    @Query("SELECT * FROM BroadcastNostrEventReceipt WHERE nostrEventId = :nostrEventId")
    fun getBroadcastNostrEventReceiptByNostrEventId(nostrEventId: String): Flow<at.torch.compose.database.model.BroadcastNostrEventReceipt?>

    @Upsert
    suspend fun upsert(broadcastNostrEventReceipt: at.torch.compose.database.model.BroadcastNostrEventReceipt): Long
}