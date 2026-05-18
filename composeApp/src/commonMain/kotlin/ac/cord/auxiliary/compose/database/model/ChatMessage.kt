package ac.cord.auxiliary.compose.database.model

import ac.cord.auxiliary.compose.database.model.traits.LocalStoreEntity
import ac.cord.auxiliary.compose.database.model.traits.SoftDeletableEntity
import ac.cord.auxiliary.compose.database.model.traits.TimestampedEntity
import ac.cord.auxiliary.compose.database.model.traits.UserViewableEntity
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.PrimaryKey
import com.vitorpamplona.quartz.nip01Core.core.HexKey
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
            entity = GiftWrapUnsigned::class,
            parentColumns = ["id"],
            childColumns = ["giftWrapUnsignedId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ChatMessage(
    @PrimaryKey
    val id: HexKey,

    val senderPublicKey: String,
    val isUserMessage: Boolean,

    val chatRoomId: String,

    val giftWrapUnsignedId: String,

    val replyToEventId: String?,
    val quotedEventId: String?,

    val content: String,

    override val createdAt: Instant,
    override val updatedAt: Instant,
    override val savedAt: Instant,
    override val viewedAt: Instant?,
    override val deletedAt: Instant?, // Who this message should be seen by (used to derive conversationId)
): TimestampedEntity, LocalStoreEntity, UserViewableEntity, SoftDeletableEntity  {
}