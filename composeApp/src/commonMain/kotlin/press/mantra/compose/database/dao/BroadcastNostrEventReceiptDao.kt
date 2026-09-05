package press.mantra.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BroadcastNostrEventReceiptDao {
    @Query("SELECT * FROM BroadcastNostrEventReceipt")
    suspend fun  getAllBroadcastNostrEventReceipts(): List<press.mantra.compose.database.model.BroadcastNostrEventReceipt>

    @Query("SELECT * FROM BroadcastNostrEventReceipt WHERE id = :id")
    fun getBroadcastNostrEventReceiptById(id: Long): Flow<press.mantra.compose.database.model.BroadcastNostrEventReceipt?>

    @Query("SELECT * FROM BroadcastNostrEventReceipt WHERE nostrEventId = :nostrEventId")
    fun getBroadcastNostrEventReceiptByNostrEventId(nostrEventId: String): Flow<press.mantra.compose.database.model.BroadcastNostrEventReceipt?>

    @Upsert
    suspend fun upsert(broadcastNostrEventReceipt: press.mantra.compose.database.model.BroadcastNostrEventReceipt): Long
}