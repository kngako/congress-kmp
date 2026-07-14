package press.mantra.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Upsert

@Dao
interface ChatMessageBroadcastNostrEventReceiptRelationDao {
    @Upsert
    suspend fun upsert(chatMessageBroadcastNostrEventReceiptRelation: press.mantra.compose.database.model.ChatMessageBroadcastNostrEventReceiptRelation)
}