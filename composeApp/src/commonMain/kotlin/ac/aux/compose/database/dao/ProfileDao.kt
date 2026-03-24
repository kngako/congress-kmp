package ac.aux.compose.database.dao

import ac.aux.compose.database.model.NostrEvent
import ac.aux.compose.database.model.Post
import ac.aux.compose.database.model.Profile
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM Profile")
    fun  getAllPosts(): List<Profile>

    @Query("SELECT * FROM Profile WHERE publicKey = :publicKey")
    fun getProfileByPublicKey(publicKey: String): Flow<Profile?>

    @Insert(
        onConflict = OnConflictStrategy.IGNORE
    )
    suspend fun insert(profile: Profile)

    @Upsert
    suspend fun upsert(profile: Profile)
}