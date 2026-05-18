package ac.cord.auxiliary.compose.database.model

import ac.cord.auxiliary.compose.database.model.traits.LocalStoreEntity
import ac.cord.auxiliary.compose.database.model.traits.NostrEventEntity
import ac.cord.auxiliary.compose.database.model.traits.SoftDeletableEntity
import ac.cord.auxiliary.compose.database.model.traits.TimestampedEntity
import ac.cord.auxiliary.compose.database.model.traits.UserViewableEntity
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.PrimaryKey
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Kind
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import kotlin.time.Instant

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = NostrEvent::class,
            parentColumns = ["id"],
            childColumns = ["nostrEventId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class GiftWrapMessage(
    @PrimaryKey
    val id: HexKey,

    /**
     * Random PublicKey (should it be derived from randomPrivateKey?)
     */
    val publicKey: String,

    val kind: Kind = 1059,
    /**
     * [
     *     ["p", receiverPublicKey, "<relay-url>"] // receiver
     *   ]
     */
    val tags: Array<Array<String>>,
    /**
     *
     * encrypt(GiftWrapSeal, randomPrivateKey, receiverPublicKey)
     */
    val content: String,
    /**
     * Signed using the randomPrivateKey used to encrypt the GiftWrapSeal
     */
    val signature: String,

    /**
     * What gets broadcasts?
     */
    override val nostrEventId: String,

    /**
     * randomTimeUpTo2DaysInThePast
     */
    override val createdAt: Instant,
    override val updatedAt: Instant,
    override val savedAt: Instant,
    override val viewedAt: Instant?,
    override val deletedAt: Instant?, // Who this message should be seen by (used to derive conversationId)
): NostrEventEntity, TimestampedEntity, LocalStoreEntity, UserViewableEntity, SoftDeletableEntity  {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as GiftWrapMessage

        if (kind != other.kind) return false
        if (id != other.id) return false
        if (publicKey != other.publicKey) return false
        if (!tags.contentDeepEquals(other.tags)) return false
        if (content != other.content) return false
        if (signature != other.signature) return false
        if (nostrEventId != other.nostrEventId) return false
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
        result = 31 * result + signature.hashCode()
        result = 31 * result + nostrEventId.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + updatedAt.hashCode()
        result = 31 * result + savedAt.hashCode()
        result = 31 * result + (viewedAt?.hashCode() ?: 0)
        result = 31 * result + (deletedAt?.hashCode() ?: 0)
        return result
    }
}