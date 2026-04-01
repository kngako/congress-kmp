package ac.aux.compose.database.dao

import ac.aux.compose.database.model.Profile
import ac.aux.compose.database.model.intermdiate.LocalAccount
import ac.aux.compose.database.model.intermdiate.LocalProfile
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ProfileDao {
    @Query("SELECT * FROM Profile LIMIT :limit")
    fun  getAllProfiles(limit: Int = 21): List<Profile>

    @Query("SELECT * FROM NostrEvent WHERE kind = 0 AND content LIKE '%' || :searchTerm || '%' LIMIT :limit")
    fun  filterProfiles(searchTerm: String, limit: Int = 21): List<LocalProfile>

    @Query("SELECT * FROM Profile WHERE publicKey = :publicKey")
    fun getProfileByPublicKey(publicKey: String): Profile?

    @Insert
    suspend fun insertPlaceholderProfile(profile: Profile)

    @Upsert
    suspend fun upsert(profile: Profile)

}