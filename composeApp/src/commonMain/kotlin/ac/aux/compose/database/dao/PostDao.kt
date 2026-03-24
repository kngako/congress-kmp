package ac.aux.compose.database.dao

import ac.aux.compose.database.model.Post
import ac.aux.compose.database.model.Profile
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import kotlinx.coroutines.flow.Flow

@Dao
interface PostDao {
    @Query("SELECT * FROM Post")
    fun  getAllPosts(): List<PostAndProfile>

    @Query("SELECT * FROM Post WHERE id = :id")
    fun getPostById(id: String): Flow<Post?>

    @Insert
    suspend fun insert(post: Post)

    data class PostAndProfile(
        @Embedded val post: Post,
        @Relation(
            parentColumn = "profilePublicKey",
            entityColumn = "publicKey"
        )
        val profile: Profile
    )
}