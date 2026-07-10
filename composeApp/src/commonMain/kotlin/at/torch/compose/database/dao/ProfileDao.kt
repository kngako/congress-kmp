package at.torch.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Upsert
import at.torch.compose.database.GENESIS_AT
import at.torch.compose.database.model.Profile
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

@Dao
interface ProfileDao {
    @Query("SELECT * FROM Profile WHERE createdAt > :createdAt LIMIT :limit")
    fun  getAllProfiles(limit: Int = 21, createdAt: Instant = GENESIS_AT): List<Profile>

    @Query("SELECT * FROM Profile WHERE publicKey NOT IN (:excludedPublicKeys) AND createdAt > :createdAt LIMIT :limit")
    fun  getAllProfilesExcludingOnesWithThesePublicKeys(
        excludedPublicKeys: Array<HexKey>,
        limit: Int = 21, createdAt: Instant = GENESIS_AT
    ): List<Profile>


    @Query("SELECT * FROM NostrEvent WHERE kind = 0 AND content LIKE '%' || :searchTerm || '%' LIMIT :limit")
    fun  filterProfiles(searchTerm: String, limit: Int = 21): List<at.torch.compose.database.model.intermdiate.LocalProfile>

    @Query("SELECT * FROM Profile WHERE publicKey = :publicKey")
    fun getProfileByPublicKey(publicKey: String): Profile?

    @Query("SELECT * FROM Profile WHERE publicKey IN (:publicKeys)")
    fun getProfileByPublicKeys(publicKeys: List<String>): List<Profile>

    @Query("SELECT * FROM NostrEvent WHERE kind = 0 AND pubKey = :publicKey")
    fun observeProfileWithFollowersByPublicKey(publicKey: String): Flow<at.torch.compose.database.model.intermdiate.LocalProfileWithFollowers?>

    @Query("SELECT * FROM NostrEvent WHERE kind = 0 AND pubKey = :publicKey")
    fun observeProfileWithFollowingByPublicKey(publicKey: String): Flow<at.torch.compose.database.model.intermdiate.LocalProfileWithFollowing?>

    @Insert
    suspend fun insertPlaceholderProfile(profile: Profile)

    @Upsert
    suspend fun upsert(profile: Profile)

}