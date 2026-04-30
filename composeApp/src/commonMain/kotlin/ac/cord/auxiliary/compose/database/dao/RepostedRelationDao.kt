package ac.cord.auxiliary.compose.database.dao

import ac.cord.auxiliary.compose.database.model.RepostedRelation
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface RepostedRelationDao {
    @Query("SELECT * FROM RepostedRelation")
    fun  getAllReposts(): List<RepostedRelation>

    @Query("SELECT * FROM RepostedRelation WHERE repostingNostrEventId = :repostingNostrEventId")
    fun getRepostByRepostingId(repostingNostrEventId: String): RepostedRelation?

    @Query("SELECT * FROM RepostedRelation WHERE repostedNostrEventId = :repostedNostrEventId")
    fun getRepostByRepostedId(repostedNostrEventId: String): RepostedRelation?

    @Upsert
    suspend fun upsert(repostedRelation: RepostedRelation)
}