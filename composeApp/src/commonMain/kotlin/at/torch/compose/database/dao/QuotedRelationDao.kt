package at.torch.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Upsert

@Dao
interface QuotedRelationDao {
    @Query("SELECT * FROM QuotedRelation WHERE quotingNostrEventId = :quotingNostrEventId")
    fun getRepostByQuotingNostrEventId(quotingNostrEventId: String): at.torch.compose.database.model.QuotedRelation?

    @Query("SELECT * FROM QuotedRelation WHERE quotedNostrEventId = :quotedNostrEventId")
    fun getRepostByQuotedNostrEventId(quotedNostrEventId: String): at.torch.compose.database.model.QuotedRelation?

    @Insert
    suspend fun insert(quotedRelation: at.torch.compose.database.model.QuotedRelation)

    @Upsert
    suspend fun upsert(quotedRelation: at.torch.compose.database.model.QuotedRelation)
}