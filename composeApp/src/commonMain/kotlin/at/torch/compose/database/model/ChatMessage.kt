package at.torch.compose.database.model

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.PrimaryKey
import at.torch.compose.database.TorchDatabase
import at.torch.compose.database.model.traits.LocalStoreEntity
import at.torch.compose.database.model.traits.SoftDeletableEntity
import at.torch.compose.database.model.traits.TimestampedEntity
import at.torch.compose.database.model.traits.UserViewableEntity
import at.torch.compose.exceptions.GiftWrapImpersonationException
import at.torch.compose.exceptions.MarmotUnprocessableInnerEventException
import at.torch.compose.exceptions.MarmotUnsupportedWireFormatException
import at.torch.compose.extensions.toHex
import com.vitorpamplona.quartz.marmot.GroupEventResult
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import kotlin.time.Clock
import kotlin.time.Instant

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = ChatRoom::class,
            parentColumns = ["id"],
            childColumns = ["chatRoomId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = GiftWrapPayload::class,
            parentColumns = ["id"],
            childColumns = ["giftWrapPayloadId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MarmotGroupEvent::class,
            parentColumns = ["id"],
            childColumns = ["marmotGroupEventId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MarmotInnerEvent::class,
            parentColumns = ["id"],
            childColumns = ["marmotInnerEventId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ChatMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val senderPublicKey: String,
    val isUserMessage: Boolean,

    val giftWrapPayloadId: HexKey?,
    val marmotGroupEventId: HexKey?,
    val marmotInnerEventId: HexKey?,
    val chatRoomId: String,

    val replyToMessageId: Long? = null,
    val quotedMessageId: Long? = null,

    val content: String,
    val messageType: String = "message",

    override val createdAt: Instant = Clock.System.now(),
    override val updatedAt: Instant = createdAt,
    override val savedAt: Instant = Clock.System.now(),
    override val viewedAt: Instant? = null,
    override val deletedAt: Instant? = null, // Who this message should be seen by (used to derive conversationId)
): TimestampedEntity, LocalStoreEntity, UserViewableEntity, SoftDeletableEntity {

    companion object {
        suspend fun fromGroupEventResult(
            database: TorchDatabase,
            activeKeyPair: KeyPair,
            groupEvent: GroupEvent,
            groupEventResult: GroupEventResult
        ): ChatMessage? {
            return when(groupEventResult) {
                is GroupEventResult.ApplicationMessage -> {
                    val event = Event.fromJsonOrNull(groupEventResult.innerEventJson)
                        ?: throw MarmotUnprocessableInnerEventException("Can't process ${groupEventResult.innerEventJson}")

                    database.marmotInnerEventDao().upsert(
                        MarmotInnerEvent(
                            id = event.id,
                            publicKey = event.pubKey,
                            marmotGroupEventId = groupEvent.id,
                            tags = event.tags,
                            content = event.content,
                            chatRoomId = groupEventResult.groupId,
                            kind = event.kind,
                            createdAt = Instant.fromEpochSeconds(event.createdAt)
                        )
                    )
                    when (event.kind) {
                        ChatEvent.KIND -> {
                            ChatMessage(
                                giftWrapPayloadId = null,
                                messageType = "message",
                                marmotGroupEventId = groupEvent.id,
                                marmotInnerEventId = event.id,
                                senderPublicKey = event.pubKey,
                                isUserMessage = activeKeyPair.pubKey.toHex() == groupEvent.pubKey,
                                chatRoomId = groupEventResult.groupId,
                                createdAt = Instant.fromEpochSeconds(event.createdAt),
                                content = event.content, // TODO: Figure out what to do here...
                            )
                        }
                        else -> {
                            ChatMessage(
                                giftWrapPayloadId = null,
                                messageType = "unsupported",
                                marmotGroupEventId = groupEvent.id,
                                marmotInnerEventId = event.id,
                                senderPublicKey = groupEvent.pubKey,
                                isUserMessage = activeKeyPair.pubKey.toHex() == groupEvent.pubKey,
                                chatRoomId = groupEventResult.groupId,
                                createdAt = Instant.fromEpochSeconds(groupEvent.createdAt),
                                content = groupEventResult.innerEventJson, // TODO: Figure out what to do here...
                            )
                        }
                    }
                }
                is GroupEventResult.CommitPending -> {
                    ChatMessage(
                        giftWrapPayloadId = null,
                        marmotGroupEventId = groupEvent.id,
                        marmotInnerEventId = null,
                        messageType = "pendingCommit",
                        senderPublicKey = groupEvent.pubKey,
                        isUserMessage = activeKeyPair.pubKey.toHex() == groupEvent.pubKey,
                        chatRoomId = groupEventResult.groupId,
                        createdAt = Instant.fromEpochSeconds(groupEvent.createdAt),
                        content = "Pending Commit in epoch ${groupEventResult.epoch}", // TODO: Figure out what to do here...
                    )
                }
                is GroupEventResult.CommitProcessed -> {
                    ChatMessage(
                        giftWrapPayloadId = null,
                        marmotGroupEventId = groupEvent.id,
                        marmotInnerEventId = null,
                        messageType = "processedCommit",
                        senderPublicKey = groupEvent.pubKey,
                        isUserMessage = activeKeyPair.pubKey.toHex() == groupEvent.pubKey,
                        chatRoomId = groupEventResult.groupId,
                        createdAt = Instant.fromEpochSeconds(groupEvent.createdAt),
                        content = "Processed Commit in epoch ${groupEventResult.newEpoch}", // TODO: Figure out what to do here...
                    )
                }
                is GroupEventResult.Duplicate -> {

//                    ChatMessage(
//                        giftWrapPayloadId = null,
//                        groupEventId = groupEvent.id,
//                        messageType = "duplicate",
//                        senderPublicKey = groupEvent.pubKey,
//                        isUserMessage = activeKeyPair.pubKey.toHex() == groupEvent.pubKey,
//                        chatRoomId = groupEventResult.groupId,
//                        createdAt = Instant.fromEpochSeconds(groupEvent.createdAt),
//                        content = groupEventResult.innerEventJson, // TODO: Figure out what to do here...
//                    )
                    null
                }
                is GroupEventResult.Error -> {
//                    ChatMessage(
//                        giftWrapPayloadId = null,
//                        groupEventId = groupEvent.id,
//                        messageType = "error",
//                        senderPublicKey = groupEvent.pubKey,
//                        isUserMessage = activeKeyPair.pubKey.toHex() == groupEvent.pubKey,
//                        chatRoomId = groupEventResult.groupId,
//                        createdAt = Instant.fromEpochSeconds(groupEvent.createdAt),
//                        content = groupEventResult.innerEventJson, // TODO: Figure out what to do here...
//                    )
                    null
                }
                is GroupEventResult.ProposalStaged -> {
                    ChatMessage(
                        giftWrapPayloadId = null,
                        marmotGroupEventId = groupEvent.id,
                        marmotInnerEventId = null,
                        messageType = "proposalStaged",
                        senderPublicKey = groupEvent.pubKey,
                        isUserMessage = activeKeyPair.pubKey.toHex() == groupEvent.pubKey,
                        chatRoomId = groupEventResult.groupId,
                        createdAt = Instant.fromEpochSeconds(groupEvent.createdAt),
                        content = "Proposal staged: ${groupEventResult.senderLeafIndex}", // TODO: Figure out what to do here...
                    )
                }
                is GroupEventResult.UndecryptableOuterLayer -> {
                    ChatMessage(
                        giftWrapPayloadId = null,
                        marmotGroupEventId = groupEvent.id,
                        marmotInnerEventId = null,
                        messageType = "undecryptableOuterLayer",
                        senderPublicKey = groupEvent.pubKey,
                        isUserMessage = activeKeyPair.pubKey.toHex() == groupEvent.pubKey,
                        chatRoomId = groupEventResult.groupId,
                        createdAt = Instant.fromEpochSeconds(groupEvent.createdAt),
                        content = "Undecryptable Message", // TODO: Figure out what to do here...
                    )
                }
            }
        }
    }
}