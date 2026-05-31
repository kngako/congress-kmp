package ac.cord.auxiliary.compose.database.dao

import ac.cord.auxiliary.compose.database.GENESIS_AT
import ac.cord.auxiliary.compose.database.model.Profile
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalProfile
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalProfileWithFollowers
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalProfileWithFollowing
import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy.Companion.IGNORE
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

@Dao
interface ProfileDao {
    @Query("SELECT * FROM Profile WHERE createdAt > :createdAt LIMIT :limit")
    fun  getAllProfiles(limit: Int = 21, createdAt: Instant = GENESIS_AT): List<Profile>

    @Query("SELECT * FROM NostrEvent WHERE kind = 0 AND content LIKE '%' || :searchTerm || '%' LIMIT :limit")
    fun  filterProfiles(searchTerm: String, limit: Int = 21): List<LocalProfile>

    @Query("SELECT * FROM Profile WHERE publicKey = :publicKey")
    fun getProfileByPublicKey(publicKey: String): Profile?

    @Query("SELECT * FROM Profile WHERE publicKey IN (:publicKeys)")
    fun getProfileByPublicKeys(publicKeys: List<String>): List<Profile>

    @Query("SELECT * FROM NostrEvent WHERE kind = 0 AND pubKey = :publicKey")
    fun observeProfileWithFollowersByPublicKey(publicKey: String): Flow<LocalProfileWithFollowers?>

    @Query("SELECT * FROM NostrEvent WHERE kind = 0 AND pubKey = :publicKey")
    fun observeProfileWithFollowingByPublicKey(publicKey: String): Flow<LocalProfileWithFollowing?>

    @Insert
    suspend fun insertPlaceholderProfile(profile: Profile)

    @Upsert
    suspend fun upsert(profile: Profile)

}