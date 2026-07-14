package press.mantra.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert

@Dao
interface RepostedRelationDao {
    @Query("SELECT * FROM RepostedRelation")
    fun  getAllReposts(): List<press.mantra.compose.database.model.RepostedRelation>

    @Query("SELECT * FROM RepostedRelation WHERE repostingNostrEventId = :repostingNostrEventId")
    fun getRepostByRepostingId(repostingNostrEventId: String): press.mantra.compose.database.model.RepostedRelation?

    @Query("SELECT * FROM RepostedRelation WHERE repostedNostrEventId = :repostedNostrEventId")
    fun getRepostByRepostedId(repostedNostrEventId: String): press.mantra.compose.database.model.RepostedRelation?

    @Upsert
    suspend fun upsert(repostedRelation: press.mantra.compose.database.model.RepostedRelation)
}