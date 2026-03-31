package ac.aux.compose.database.dao

import ac.aux.compose.database.model.Post
import ac.aux.compose.database.model.Profile
import ac.aux.compose.database.model.intermdiate.LocalPost
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PostDao {
    @Query("SELECT * FROM Post")
    fun  getAllPosts(): List<LocalPost>

    @Query("SELECT * FROM Post WHERE id = :id")
    fun getPostById(id: String): Post?

    @Insert
    suspend fun insert(post: Post)

    @Upsert
    suspend fun upsert(post: Post)
}