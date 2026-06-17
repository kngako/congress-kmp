package at.torch.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert

@Dao
interface RepostedRelationDao {
    @Query("SELECT * FROM RepostedRelation")
    fun  getAllReposts(): List<at.torch.compose.database.model.RepostedRelation>

    @Query("SELECT * FROM RepostedRelation WHERE repostingNostrEventId = :repostingNostrEventId")
    fun getRepostByRepostingId(repostingNostrEventId: String): at.torch.compose.database.model.RepostedRelation?

    @Query("SELECT * FROM RepostedRelation WHERE repostedNostrEventId = :repostedNostrEventId")
    fun getRepostByRepostedId(repostedNostrEventId: String): at.torch.compose.database.model.RepostedRelation?

    @Upsert
    suspend fun upsert(repostedRelation: at.torch.compose.database.model.RepostedRelation)
}