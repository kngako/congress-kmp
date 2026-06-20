package at.torch.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

@Dao
interface ProfileDao {
    @Query("SELECT * FROM Profile WHERE createdAt > :createdAt LIMIT :limit")
    fun  getAllProfiles(limit: Int = 21, createdAt: Instant = _root_ide_package_.at.torch.compose.database.GENESIS_AT): List<at.torch.compose.database.model.Profile>

    @Query("SELECT * FROM NostrEvent WHERE kind = 0 AND content LIKE '%' || :searchTerm || '%' LIMIT :limit")
    fun  filterProfiles(searchTerm: String, limit: Int = 21): List<at.torch.compose.database.model.intermdiate.LocalProfile>

    @Query("SELECT * FROM Profile WHERE publicKey = :publicKey")
    fun getProfileByPublicKey(publicKey: String): at.torch.compose.database.model.Profile?

    @Query("SELECT * FROM Profile WHERE publicKey IN (:publicKeys)")
    fun getProfileByPublicKeys(publicKeys: List<String>): List<at.torch.compose.database.model.Profile>

    @Query("SELECT * FROM NostrEvent WHERE kind = 0 AND pubKey = :publicKey")
    fun observeProfileWithFollowersByPublicKey(publicKey: String): Flow<at.torch.compose.database.model.intermdiate.LocalProfileWithFollowers?>

    @Query("SELECT * FROM NostrEvent WHERE kind = 0 AND pubKey = :publicKey")
    fun observeProfileWithFollowingByPublicKey(publicKey: String): Flow<at.torch.compose.database.model.intermdiate.LocalProfileWithFollowing?>

    @Insert
    suspend fun insertPlaceholderProfile(profile: at.torch.compose.database.model.Profile)

    @Upsert
    suspend fun upsert(profile: at.torch.compose.database.model.Profile)

}