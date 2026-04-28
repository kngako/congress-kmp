package ac.cord.auxiliary.compose.database.model

import ac.cord.auxiliary.compose.database.model.traits.BroadcastableEntity
import ac.cord.auxiliary.compose.database.model.traits.LocalStoreEntity
import ac.cord.auxiliary.compose.database.model.traits.NostrEventEntity
import ac.cord.auxiliary.compose.database.model.traits.ProfilePublicKeyEntity
import ac.cord.auxiliary.compose.database.model.traits.SoftDeletableEntity
import ac.cord.auxiliary.compose.database.model.traits.TimestampedEntity
import ac.cord.auxiliary.compose.database.model.traits.UserViewableEntity
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import kotlin.time.Clock
import kotlin.time.Instant

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = Profile::class,
            parentColumns = ["publicKey"],
            childColumns = ["profilePublicKey"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Profile::class,
            parentColumns = ["publicKey"],
            childColumns = ["inReplyToPostAuthorPublicKey"],
            onDelete = ForeignKey.CASCADE,
        ),
        // This might cause problems... but we gonna make the feed so cool if it works...
        ForeignKey(
            entity = NostrEvent::class,
            parentColumns = ["id"],
            childColumns = ["nostrEventId"],
            onDelete = ForeignKey.CASCADE,
        ),
        // Support post replies
        ForeignKey(
            entity = Post::class,
            parentColumns = ["id"],
            childColumns = ["inReplyToPostId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("profilePublicKey"),
        Index("nostrEventId"),
        Index("inReplyToPostId")
    ],
)
data class InReplyToPost(
    @PrimaryKey
    val id: HexKey,

    val inReplyToPostId: HexKey,
    val inReplyToPostTags: TagArray,
    val inReplyToPostCreatedAt: Instant,
    val inReplyToPostContent: String,
    val inReplyToPostAuthorPublicKey: HexKey,

    val content: String,

    override val nostrEventId: HexKey, // Might be a replaceable nostr event?
    override val profilePublicKey: HexKey,

    override val createdAt: Instant = Clock.System.now(),
    override val updatedAt: Instant = Clock.System.now(),
    override val savedAt: Instant = Clock.System.now(),
    override val viewedAt: Instant? = null,
    override val deletedAt: Instant? = null,
    override val broadcastedAt: Instant? = null,
): NostrEventEntity, ProfilePublicKeyEntity, TimestampedEntity, LocalStoreEntity, UserViewableEntity, BroadcastableEntity, SoftDeletableEntity {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as InReplyToPost

        if (id != other.id) return false
        if (inReplyToPostId != other.inReplyToPostId) return false
        if (!inReplyToPostTags.contentDeepEquals(other.inReplyToPostTags)) return false
        if (inReplyToPostCreatedAt != other.inReplyToPostCreatedAt) return false
        if (inReplyToPostContent != other.inReplyToPostContent) return false
        if (inReplyToPostAuthorPublicKey != other.inReplyToPostAuthorPublicKey) return false
        if (content != other.content) return false
        if (nostrEventId != other.nostrEventId) return false
        if (profilePublicKey != other.profilePublicKey) return false
        if (createdAt != other.createdAt) return false
        if (updatedAt != other.updatedAt) return false
        if (savedAt != other.savedAt) return false
        if (viewedAt != other.viewedAt) return false
        if (deletedAt != other.deletedAt) return false
        if (broadcastedAt != other.broadcastedAt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + inReplyToPostId.hashCode()
        result = 31 * result + inReplyToPostTags.contentDeepHashCode()
        result = 31 * result + inReplyToPostCreatedAt.hashCode()
        result = 31 * result + inReplyToPostContent.hashCode()
        result = 31 * result + inReplyToPostAuthorPublicKey.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + nostrEventId.hashCode()
        result = 31 * result + profilePublicKey.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + updatedAt.hashCode()
        result = 31 * result + savedAt.hashCode()
        result = 31 * result + (viewedAt?.hashCode() ?: 0)
        result = 31 * result + (deletedAt?.hashCode() ?: 0)
        result = 31 * result + (broadcastedAt?.hashCode() ?: 0)
        return result
    }

}