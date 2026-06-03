package ac.cord.auxiliary.compose.database.dao

import ac.cord.auxiliary.compose.database.model.ChatMessageBroadcastNostrEventReceiptRelation
import androidx.room3.Dao
import androidx.room3.Upsert

@Dao
interface ChatMessageBroadcastNostrEventReceiptRelationDao {
    @Upsert
    suspend fun upsert(chatMessageBroadcastNostrEventReceiptRelation: ChatMessageBroadcastNostrEventReceiptRelation)
}