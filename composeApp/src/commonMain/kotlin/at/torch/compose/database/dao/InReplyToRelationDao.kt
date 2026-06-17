package at.torch.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Upsert

@Dao
interface InReplyToRelationDao {
    @Query("SELECT * FROM InReplyToRelation WHERE replyingNostrEventId = :replyingNostrEventId")
    fun getRepostByReplyingNostrEventId(replyingNostrEventId: String): at.torch.compose.database.model.InReplyToRelation?

    @Query("SELECT * FROM InReplyToRelation WHERE inReplyToNostrEventId = :inReplyToNostrEventId")
    fun getRepostByInReplyToNostrEventId(inReplyToNostrEventId: String): at.torch.compose.database.model.InReplyToRelation?

    @Insert
    suspend fun insert(inReplyToRelation: at.torch.compose.database.model.InReplyToRelation)

    @Upsert
    suspend fun upsert(inReplyToRelation: at.torch.compose.database.model.InReplyToRelation)
}