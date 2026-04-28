package ac.cord.auxiliary.compose.database.dao

import ac.cord.auxiliary.compose.database.model.Mention
import ac.cord.auxiliary.compose.database.model.Post
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalPost
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface MentionDao {

    @Insert
    suspend fun insert(mention: Mention)

    @Upsert
    suspend fun upsert(mention: Mention)
}