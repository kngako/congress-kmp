package ac.cord.auxiliary.compose.database.dao

import ac.cord.auxiliary.compose.database.model.ChatMessageBroadcastNostrEventRequestRelation
import ac.cord.auxiliary.compose.database.model.NostrEvent
import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Kind
import kotlin.time.Instant

@Dao
interface ChatMessageBroadcastNostrEventRequestRelationDao {
    @Query("SELECT * FROM ChatMessageBroadcastNostrEventRequestRelation WHERE broadcastNostrEventRequestId = :broadcastNostrEventRequestId")
    suspend fun getChatMessageBroadcastNostrEventRequestRelationByBroadcastRequest(broadcastNostrEventRequestId: Long): ChatMessageBroadcastNostrEventRequestRelation?

    @Upsert
    suspend fun upsert(chatMessageBroadcastNostrEventRequestRelation: ChatMessageBroadcastNostrEventRequestRelation)
}