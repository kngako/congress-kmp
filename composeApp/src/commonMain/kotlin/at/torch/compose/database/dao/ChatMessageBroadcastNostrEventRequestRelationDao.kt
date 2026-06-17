package at.torch.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert

@Dao
interface ChatMessageBroadcastNostrEventRequestRelationDao {
    @Query("SELECT * FROM ChatMessageBroadcastNostrEventRequestRelation WHERE broadcastNostrEventRequestId = :broadcastNostrEventRequestId")
    suspend fun getChatMessageBroadcastNostrEventRequestRelationByBroadcastRequest(broadcastNostrEventRequestId: Long): at.torch.compose.database.model.ChatMessageBroadcastNostrEventRequestRelation?

    @Upsert
    suspend fun upsert(chatMessageBroadcastNostrEventRequestRelation: at.torch.compose.database.model.ChatMessageBroadcastNostrEventRequestRelation)
}