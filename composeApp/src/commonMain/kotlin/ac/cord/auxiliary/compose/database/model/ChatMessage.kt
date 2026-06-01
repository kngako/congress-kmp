package ac.cord.auxiliary.compose.database.model

import ac.cord.auxiliary.compose.database.model.traits.LocalStoreEntity
import ac.cord.auxiliary.compose.database.model.traits.SoftDeletableEntity
import ac.cord.auxiliary.compose.database.model.traits.TimestampedEntity
import ac.cord.auxiliary.compose.database.model.traits.UserViewableEntity
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.PrimaryKey
import com.vitorpamplona.quartz.nip01Core.core.HexKey
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
    ],
)
data class ChatMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val senderPublicKey: String,
    val isUserMessage: Boolean,

    val chatRoomId: String,
    val giftWrapPayloadId: String? = null,

    val replyToMessageId: Long? = null,
    val quotedMessageId: Long? = null,

    val content: String,

    override val createdAt: Instant = Clock.System.now(),
    override val updatedAt: Instant = createdAt,
    override val savedAt: Instant = Clock.System.now(),
    override val viewedAt: Instant? = null,
    override val deletedAt: Instant? = null, // Who this message should be seen by (used to derive conversationId)
): TimestampedEntity, LocalStoreEntity, UserViewableEntity, SoftDeletableEntity  {
}