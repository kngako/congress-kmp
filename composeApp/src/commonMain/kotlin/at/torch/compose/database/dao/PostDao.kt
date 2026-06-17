package at.torch.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Upsert

@Dao
interface PostDao {
    @Query("SELECT * FROM Post")
    fun  getAllPosts(): List<at.torch.compose.database.model.intermdiate.LocalPost>

    @Query("SELECT * FROM Post WHERE id = :id")
    fun getPostById(id: String): at.torch.compose.database.model.Post?

    @Insert
    suspend fun insert(post: at.torch.compose.database.model.Post)

    @Upsert
    suspend fun upsert(post: at.torch.compose.database.model.Post)
}