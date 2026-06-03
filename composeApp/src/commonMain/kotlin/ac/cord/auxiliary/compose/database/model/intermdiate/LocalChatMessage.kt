package ac.cord.auxiliary.compose.database.model.intermdiate

import ac.cord.auxiliary.compose.database.model.ChatMessage
import ac.cord.auxiliary.compose.database.model.ChatMessageBroadcastNostrEventReceiptRelation
import ac.cord.auxiliary.compose.database.model.ChatMessageBroadcastNostrEventRequestRelation
import ac.cord.auxiliary.compose.database.model.ChatMessageNostrEventRelation
import ac.cord.auxiliary.compose.database.model.GiftWrapPayload
import ac.cord.auxiliary.compose.database.model.Profile
import androidx.room3.Embedded
import androidx.room3.Relation

data class LocalChatMessage(
    @Embedded val chatMessage: ChatMessage,

    @Relation(
        parentColumns = ["senderPublicKey"],
        entityColumns = ["publicKey"]
    )
    val profile: Profile?,

    @Relation(
        parentColumns = ["id"],
        entityColumns = ["chatMessageId"]
    )
    val chatMessageBroadcastNostrEventReceiptRelation: ChatMessageBroadcastNostrEventReceiptRelation? = null,


    @Relation(
        parentColumns = ["id"],
        entityColumns = ["chatMessageId"]
    )
    val chatMessageBroadcastNostrEventRequestRelation: ChatMessageBroadcastNostrEventRequestRelation? = null,


    @Relation(
        parentColumns = ["id"],
        entityColumns = ["chatMessageId"]
    )
    val chatMessageNostrEventRelation: ChatMessageNostrEventRelation? = null,
)
