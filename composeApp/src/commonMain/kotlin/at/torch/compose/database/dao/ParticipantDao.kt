package at.torch.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Query
import androidx.room3.Upsert
import at.torch.compose.database.model.Participant

@Dao
interface ParticipantDao {
    @Query("SELECT * FROM Participant WHERE participantPublicKey = :participantPublicKey")
    fun findParticipantByPublicKey(participantPublicKey: String): List<Participant>

    @Query("SELECT * FROM Participant WHERE chatRoomId = :chatRoomId")
    fun findParticipantsByChatRoomId(chatRoomId: String): List<Participant>

    @Upsert
    suspend fun upsert(participants: List<Participant>)

    @Delete
    suspend fun delete(participants: List<Participant>)

}