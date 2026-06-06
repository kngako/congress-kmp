package ac.cord.auxiliary.compose.database.dao

import ac.cord.auxiliary.compose.database.model.ChatRoom
import ac.cord.auxiliary.compose.database.model.Participant
import ac.cord.auxiliary.compose.database.model.Relay
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalChatRoom
import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import fr.acinq.bitcoin.PublicKey
import kotlinx.coroutines.flow.Flow

@Dao
interface ParticipantDao {
    @Query("SELECT * FROM Participant WHERE participantPublicKey = :participantPublicKey")
    fun findParticipantByPublicKey(participantPublicKey: String): Participant?

    @Query("SELECT * FROM Participant WHERE chatRoomId = :chatRoomId")
    fun findParticipantsByChatRoomId(chatRoomId: String): List<Participant>

    @Upsert
    suspend fun upsert(participants: List<Participant>)

}