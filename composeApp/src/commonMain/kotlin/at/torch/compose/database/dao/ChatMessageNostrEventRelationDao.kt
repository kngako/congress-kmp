package at.torch.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Upsert

@Dao
interface ChatMessageNostrEventRelationDao {

    @Upsert
    suspend fun upsert(chatMessageNostrEventRelation: at.torch.compose.database.model.ChatMessageNostrEventRelation)
}