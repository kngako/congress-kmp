package at.torch.compose.database.model.intermdiate

import androidx.room3.Embedded
import androidx.room3.Relation

data class LocalChatMessage(
    @Embedded val chatMessage: at.torch.compose.database.model.ChatMessage,

    @Relation(
        parentColumns = ["senderPublicKey"],
        entityColumns = ["publicKey"]
    )
    val profile: at.torch.compose.database.model.Profile?,

    @Relation(
        parentColumns = ["id"],
        entityColumns = ["chatMessageId"]
    )
    val chatMessageBroadcastNostrEventReceiptRelation: at.torch.compose.database.model.ChatMessageBroadcastNostrEventReceiptRelation? = null,


    @Relation(
        parentColumns = ["id"],
        entityColumns = ["chatMessageId"]
    )
    val chatMessageBroadcastNostrEventRequestRelation: at.torch.compose.database.model.ChatMessageBroadcastNostrEventRequestRelation? = null,


    @Relation(
        parentColumns = ["id"],
        entityColumns = ["chatMessageId"]
    )
    val chatMessageNostrEventRelation: at.torch.compose.database.model.ChatMessageNostrEventRelation? = null,
)
