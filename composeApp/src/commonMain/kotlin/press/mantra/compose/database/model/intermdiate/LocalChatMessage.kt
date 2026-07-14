package press.mantra.compose.database.model.intermdiate

import androidx.room3.Embedded
import androidx.room3.Relation

data class LocalChatMessage(
    @Embedded val chatMessage: press.mantra.compose.database.model.ChatMessage,

    @Relation(
        parentColumns = ["senderPublicKey"],
        entityColumns = ["publicKey"]
    )
    val profile: press.mantra.compose.database.model.Profile?,

    @Relation(
        parentColumns = ["id"],
        entityColumns = ["chatMessageId"]
    )
    val chatMessageBroadcastNostrEventReceiptRelation: press.mantra.compose.database.model.ChatMessageBroadcastNostrEventReceiptRelation? = null,


    @Relation(
        parentColumns = ["id"],
        entityColumns = ["chatMessageId"]
    )
    val chatMessageBroadcastNostrEventRequestRelation: press.mantra.compose.database.model.ChatMessageBroadcastNostrEventRequestRelation? = null,


    @Relation(
        parentColumns = ["id"],
        entityColumns = ["chatMessageId"]
    )
    val chatMessageNostrEventRelation: press.mantra.compose.database.model.ChatMessageNostrEventRelation? = null,
)
