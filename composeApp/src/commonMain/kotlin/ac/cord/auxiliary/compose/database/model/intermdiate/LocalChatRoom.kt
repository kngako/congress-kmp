package ac.cord.auxiliary.compose.database.model.intermdiate

import ac.cord.auxiliary.compose.database.model.ChatRoom
import ac.cord.auxiliary.compose.database.model.Mention
import ac.cord.auxiliary.compose.database.model.Participant
import ac.cord.auxiliary.compose.database.model.Post
import ac.cord.auxiliary.compose.database.model.Profile
import androidx.room3.Embedded
import androidx.room3.Relation

data class LocalChatRoom(
    @Embedded val chatRoom: ChatRoom,

    @Relation(
        entity = Participant::class,
        parentColumns = ["id"],
        entityColumns = ["chatRoomId"],
    )
    val participants: List<LocalParticipant> = emptyList(),
)
