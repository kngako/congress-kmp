package ac.cord.auxiliary.compose.database.dao

import ac.cord.auxiliary.compose.database.model.InReplyToPost
import ac.cord.auxiliary.compose.database.model.Post
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalPost
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface InReplyToPostDao {
    @Insert
    suspend fun insert(inReplyToPost: InReplyToPost)

    @Upsert
    suspend fun upsert(inReplyToPost: InReplyToPost)
}