package ac.cord.auxiliary.compose.database.dao

import ac.cord.auxiliary.compose.database.model.Profile
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalProfile
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalProfileWithFollowers
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalProfileWithFollowing
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

    @Query("SELECT * FROM NostrEvent WHERE kind = 0 AND pubKey = :publicKey")
    fun observeProfileWithFollowersByPublicKey(publicKey: String): LocalProfileWithFollowers?

    @Query("SELECT * FROM NostrEvent WHERE kind = 0 AND pubKey = :publicKey")
    fun observeProfileWithFollowingByPublicKey(publicKey: String): LocalProfileWithFollowing?

    @Insert
    suspend fun insertPlaceholderProfile(profile: Profile)

    @Upsert
    suspend fun upsert(profile: Profile)

}