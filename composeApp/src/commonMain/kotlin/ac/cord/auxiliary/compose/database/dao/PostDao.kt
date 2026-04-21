package ac.cord.auxiliary.compose.database.dao

import ac.cord.auxiliary.compose.database.model.Post
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalPost
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert

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