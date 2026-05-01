package ac.cord.auxiliary.compose.database.dao

import ac.cord.auxiliary.compose.database.model.InReplyToRelation
import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Upsert

@Dao
interface InReplyToRelationDao {
    @Query("SELECT * FROM InReplyToRelation WHERE replyingNostrEventId = :replyingNostrEventId")
    fun getRepostByReplyingNostrEventId(replyingNostrEventId: String): InReplyToRelation?

    @Query("SELECT * FROM InReplyToRelation WHERE inReplyToNostrEventId = :inReplyToNostrEventId")
    fun getRepostByInReplyToNostrEventId(inReplyToNostrEventId: String): InReplyToRelation?

    @Insert
    suspend fun insert(inReplyToRelation: InReplyToRelation)

    @Upsert
    suspend fun upsert(inReplyToRelation: InReplyToRelation)
}