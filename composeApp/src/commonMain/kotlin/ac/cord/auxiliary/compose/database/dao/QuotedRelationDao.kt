package ac.cord.auxiliary.compose.database.dao

import ac.cord.auxiliary.compose.database.model.QuotedRelation
import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Upsert

@Dao
interface QuotedRelationDao {
    @Query("SELECT * FROM QuotedRelation WHERE quotingNostrEventId = :quotingNostrEventId")
    fun getRepostByQuotingNostrEventId(quotingNostrEventId: String): QuotedRelation?

    @Query("SELECT * FROM QuotedRelation WHERE quotedNostrEventId = :quotedNostrEventId")
    fun getRepostByQuotedNostrEventId(quotedNostrEventId: String): QuotedRelation?

    @Insert
    suspend fun insert(quotedRelation: QuotedRelation)

    @Upsert
    suspend fun upsert(quotedRelation: QuotedRelation)
}