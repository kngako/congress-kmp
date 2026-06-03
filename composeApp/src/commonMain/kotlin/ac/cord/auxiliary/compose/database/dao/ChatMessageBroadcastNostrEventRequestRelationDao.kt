package ac.cord.auxiliary.compose.database.dao

import ac.cord.auxiliary.compose.database.model.ChatMessageBroadcastNostrEventRequestRelation
import androidx.room3.Dao
import androidx.room3.Upsert

@Dao
interface ChatMessageBroadcastNostrEventRequestRelationDao {

    @Upsert
    suspend fun upsert(chatMessageBroadcastNostrEventRequestRelation: ChatMessageBroadcastNostrEventRequestRelation)
}