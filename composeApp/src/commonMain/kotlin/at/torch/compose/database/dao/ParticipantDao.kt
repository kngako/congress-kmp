package at.torch.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert

@Dao
interface ParticipantDao {
    @Query("SELECT * FROM Participant WHERE participantPublicKey = :participantPublicKey")
    fun findParticipantByPublicKey(participantPublicKey: String): at.torch.compose.database.model.Participant?

    @Query("SELECT * FROM Participant WHERE chatRoomId = :chatRoomId")
    fun findParticipantsByChatRoomId(chatRoomId: String): List<at.torch.compose.database.model.Participant>

    @Upsert
    suspend fun upsert(participants: List<at.torch.compose.database.model.Participant>)

}