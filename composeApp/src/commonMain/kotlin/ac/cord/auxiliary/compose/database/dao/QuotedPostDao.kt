package ac.cord.auxiliary.compose.database.dao

import ac.cord.auxiliary.compose.database.model.Post
import ac.cord.auxiliary.compose.database.model.QuotedPost
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalPost
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface QuotedPostDao {
    @Insert
    suspend fun insert(quotedPost: QuotedPost)

    @Upsert
    suspend fun upsert(quotedPost: QuotedPost)
}