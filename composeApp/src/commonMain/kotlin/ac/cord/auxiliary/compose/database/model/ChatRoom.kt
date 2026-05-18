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
            entity = Profile::class,
            parentColumns = ["publicKey"],
            childColumns = ["userPublicKey"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = GiftWrapUnsigned::class,
            parentColumns = ["id"],
            childColumns = ["initialGiftWrapUnsignedId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ChatRoom(
    /**
     * Derived from the participants...
     *
     * can aggregate them into one id like musig(participants...)
     */
    @PrimaryKey
    val id: String,

    /**
     * Logged in user PublicKey... should help us have multiple user support...
     */
    val userPublicKey: HexKey,

    /**
     * Optional retrieved from tags...
     *
     *  Any member can change the topic by simply submitting a new subject to an existing pubkey + p tags room.
     *
     *  There is no need to send subject in every message. The newest subject in the chat room is the subject of the conversation.
     */
    val subject: String?,

    /**
     * Should probably be the human-readable name of whichever participant is not the user...
     */
    val name: String?,

    /**
     * Derived from kind14/15 tags
     * [
     *     ["p", "<receiver-1-pubkey>", "<relay-url>"],
     *     ["p", "<receiver-2-pubkey>", "<relay-url>"],
     *     // rest of tags...
     *   ]
     *
     *   TODO: Might want to add a relation for this...
     */
    // TODO: Add a LastChatMessageRelation
//    val lastChatMessageId: String? = null,
    // Might want to focus on the other last messages...
    val initialGiftWrapUnsignedId: String,

    override val createdAt: Instant,
    override val updatedAt: Instant,
    override val savedAt: Instant,
    override val viewedAt: Instant?,
    override val deletedAt: Instant?,

): TimestampedEntity, LocalStoreEntity, UserViewableEntity, SoftDeletableEntity {
}