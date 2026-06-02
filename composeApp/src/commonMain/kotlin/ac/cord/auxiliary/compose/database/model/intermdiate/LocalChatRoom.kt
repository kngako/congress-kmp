package ac.cord.auxiliary.compose.database.model.intermdiate

import ac.cord.auxiliary.compose.database.model.ChatRoom
import ac.cord.auxiliary.compose.database.model.Mention
import ac.cord.auxiliary.compose.database.model.Participant
import ac.cord.auxiliary.compose.database.model.Post
import ac.cord.auxiliary.compose.database.model.Profile
import ac.cord.auxiliary.compose.ui.composable.widgets.profile.ProfileColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
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
) {
    @Composable
    fun RenderChatRoomTitleText() {
        if (chatRoom.subject != null) {
            Text(
                text = chatRoom.subject,
                color = ProfileColor.fromPublicKey(chatRoom.id)
            )
        } else {
            if (participants.size == 1) {
                Text(
                    text = "Note to Self (${participants.first().profile?.humanReadableNameOrPubkey()})"
                )
            } else {
                // Remove the active user publicKey from participants...
                val participantNames = participants.filter { it.participant.participantPublicKey != chatRoom.userPublicKey }.map { buildAnnotatedString {
                    withStyle(style = SpanStyle(color = ProfileColor.fromPublicKey(it.participant.participantPublicKey))) {
                        append(it.profile?.humanReadableNameOrPubkey() ?: "unknown participant")
                    }
                } }

                Text(
                    text = buildAnnotatedString {
                        participantNames.forEachIndexed { index, participantName ->
                            if (index != 0) {
                                append(", ")
                            }
                            append(participantName)
                        }
                    },
                )
            }
        }
    }
}
