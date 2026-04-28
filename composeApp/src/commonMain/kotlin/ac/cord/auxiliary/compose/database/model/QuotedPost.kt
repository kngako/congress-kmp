package ac.cord.auxiliary.compose.database.model

import ac.cord.auxiliary.compose.database.model.traits.BroadcastableEntity
import ac.cord.auxiliary.compose.database.model.traits.LocalStoreEntity
import ac.cord.auxiliary.compose.database.model.traits.NostrEventEntity
import ac.cord.auxiliary.compose.database.model.traits.ProfilePublicKeyEntity
import ac.cord.auxiliary.compose.database.model.traits.SoftDeletableEntity
import ac.cord.auxiliary.compose.database.model.traits.TimestampedEntity
import ac.cord.auxiliary.compose.database.model.traits.UserViewableEntity
import ac.cord.auxiliary.compose.extensions.toFormattedTimeAndDateString
import ac.cord.auxiliary.compose.ui.composable.widgets.profile.ProfileAvatar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
            childColumns = ["quotedPostAuthorPublicKey"],
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
            childColumns = ["quotedPostId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("profilePublicKey"),
        Index("nostrEventId"),
        Index("quotedPostId")
    ],
)
data class QuotedPost(
    @PrimaryKey
    val id: HexKey,

    val quotedPostId: HexKey,
    val quotedPostTags: TagArray,
    val quotedPostCreatedAt: Instant,
    val quotedPostContent: String,
    val quotedPostAuthorPublicKey: HexKey,

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

        other as QuotedPost

        if (id != other.id) return false
        if (quotedPostId != other.quotedPostId) return false
        if (!quotedPostTags.contentDeepEquals(other.quotedPostTags)) return false
        if (quotedPostCreatedAt != other.quotedPostCreatedAt) return false
        if (quotedPostContent != other.quotedPostContent) return false
        if (quotedPostAuthorPublicKey != other.quotedPostAuthorPublicKey) return false
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
        result = 31 * result + quotedPostId.hashCode()
        result = 31 * result + quotedPostTags.contentDeepHashCode()
        result = 31 * result + quotedPostCreatedAt.hashCode()
        result = 31 * result + quotedPostContent.hashCode()
        result = 31 * result + quotedPostAuthorPublicKey.hashCode()
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

    @Composable
    fun RenderNotePreview() {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

//                TODO ProfileAvatar(
//                    profile = profile,
//                    publicKey = pubKey
//                )

//                Column(
//                    modifier = Modifier.weight(1f)
//                ) {
//                    Text(
//                        text = profile?.displayName ?: profile?.userName ?: nostrEvent.pubKey,
//                        style = MaterialTheme.typography.labelLarge
//                    )
//
//                    profile?.nip05?.let {
//                        Text(
//                            text = profile.nip05 ?: "",
//                            style = MaterialTheme.typography.labelMedium
//                        )
//                    }
//                }
                Text(
                    text = "Loading Profile information"
                )
            }
            Text(
                modifier = Modifier.fillMaxWidth().padding(
                    horizontal = 20.dp
                ),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Start,
                overflow = TextOverflow.Ellipsis,
                text = content
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    text = createdAt.toFormattedTimeAndDateString(),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}