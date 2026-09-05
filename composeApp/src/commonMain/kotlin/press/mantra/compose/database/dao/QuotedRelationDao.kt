package press.mantra.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Upsert

@Dao
interface QuotedRelationDao {
    @Query("SELECT * FROM QuotedRelation WHERE quotingNostrEventId = :quotingNostrEventId")
    suspend fun getRepostByQuotingNostrEventId(quotingNostrEventId: String): press.mantra.compose.database.model.QuotedRelation?

    @Query("SELECT * FROM QuotedRelation WHERE quotedNostrEventId = :quotedNostrEventId")
    suspend fun getRepostByQuotedNostrEventId(quotedNostrEventId: String): press.mantra.compose.database.model.QuotedRelation?

    @Insert
    suspend fun insert(quotedRelation: press.mantra.compose.database.model.QuotedRelation)

    @Upsert
    suspend fun upsert(quotedRelation: press.mantra.compose.database.model.QuotedRelation)
}