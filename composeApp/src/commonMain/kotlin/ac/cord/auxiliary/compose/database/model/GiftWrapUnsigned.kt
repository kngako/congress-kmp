package ac.cord.auxiliary.compose.database.model

import ac.cord.auxiliary.compose.database.model.traits.LocalStoreEntity
import ac.cord.auxiliary.compose.database.model.traits.SoftDeletableEntity
import ac.cord.auxiliary.compose.database.model.traits.TimestampedEntity
import ac.cord.auxiliary.compose.database.model.traits.UserViewableEntity
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.PrimaryKey
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Kind
import kotlin.time.Instant

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = GiftWrapSeal::class,
            parentColumns = ["id"],
            childColumns = ["giftWrapSealId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class GiftWrapUnsigned(
    @PrimaryKey
    val id: HexKey,

    /**
     * Sender Public Key
     */
    val publicKey: String,

    /**
     * Chat Message Kind
     */
    val kind: Kind = 14,

    /**
     * Tags for the unsigned giftwrap
     *
     * [
     *     ["p", "<receiver-1-pubkey>", "<relay-url>"],
     *     ["p", "<receiver-2-pubkey>", "<relay-url>"],
     *     ["e", "<kind-14-id>", "<relay-url>"] // if this is a reply
     *     ["subject", "<conversation-title>"],
     *     // rest of tags...
     *   ]
     *
     *   q tags MAY be used when citing events in the .content with NIP-21.
     */
    val tags: Array<Array<String>>,

    /**
     * content MUST be plain text. Fields id and created_at are required.
     */
    val content: String,
    val quotedEventId: String?,

    /**
     * Associated GiftWrapSeal
     */
    val giftWrapSealId: String? = null,

    /**
     * Current time
     */
    override val createdAt: Instant,
    override val updatedAt: Instant,
    override val savedAt: Instant,
    override val viewedAt: Instant?,
    override val deletedAt: Instant?, // Who this message should be seen by (used to derive conversationId)
): TimestampedEntity, LocalStoreEntity, UserViewableEntity, SoftDeletableEntity {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as GiftWrapUnsigned

        if (kind != other.kind) return false
        if (id != other.id) return false
        if (publicKey != other.publicKey) return false
        if (!tags.contentDeepEquals(other.tags)) return false
        if (content != other.content) return false
        if (quotedEventId != other.quotedEventId) return false
        if (giftWrapSealId != other.giftWrapSealId) return false
        if (createdAt != other.createdAt) return false
        if (updatedAt != other.updatedAt) return false
        if (savedAt != other.savedAt) return false
        if (viewedAt != other.viewedAt) return false
        if (deletedAt != other.deletedAt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = kind
        result = 31 * result + id.hashCode()
        result = 31 * result + publicKey.hashCode()
        result = 31 * result + tags.contentDeepHashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + (quotedEventId?.hashCode() ?: 0)
        result = 31 * result + (giftWrapSealId?.hashCode() ?: 0)
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + updatedAt.hashCode()
        result = 31 * result + savedAt.hashCode()
        result = 31 * result + (viewedAt?.hashCode() ?: 0)
        result = 31 * result + (deletedAt?.hashCode() ?: 0)
        return result
    }
}