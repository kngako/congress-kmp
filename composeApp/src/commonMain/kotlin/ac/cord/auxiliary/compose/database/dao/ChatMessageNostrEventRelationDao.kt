package ac.cord.auxiliary.compose.database.dao

import ac.cord.auxiliary.compose.database.model.ChatMessageNostrEventRelation
import androidx.room3.Dao
import androidx.room3.Upsert

@Dao
interface ChatMessageNostrEventRelationDao {

    @Upsert
    suspend fun upsert(chatMessageNostrEventRelation: ChatMessageNostrEventRelation)
}