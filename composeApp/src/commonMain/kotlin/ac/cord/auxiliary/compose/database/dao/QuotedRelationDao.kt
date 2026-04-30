package ac.cord.auxiliary.compose.database.dao

import ac.cord.auxiliary.compose.database.model.QuotedRelation
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert

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