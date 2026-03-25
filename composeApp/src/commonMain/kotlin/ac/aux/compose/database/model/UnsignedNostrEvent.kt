package ac.aux.compose.database.model

import ac.aux.compose.database.model.traits.BroadcastableEntity
import ac.aux.compose.database.model.traits.LocalStoreEntity
import ac.aux.compose.database.model.traits.SoftDeletableEntity
import ac.aux.compose.database.model.traits.TimestampedEntity
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Kind
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.String
import kotlin.time.Clock
import kotlin.time.Instant

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = NostrEvent::class,
            parentColumns = ["id"],
            childColumns = ["signedNostrEventId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index("signedNostrEventId"),
    ],
)
data class UnsignedNostrEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val kind: Kind,
    val tags: TagArray,
    val content: String,

    val signedNostrEventId: HexKey? = null,

    override val createdAt: Instant = Clock.System.now(),
    override val updatedAt: Instant = Clock.System.now(),
    override val savedAt: Instant = Clock.System.now(),
    override val deletedAt: Instant? = null,
    override val broadcastedAt: Instant? = null,
): TimestampedEntity, LocalStoreEntity, BroadcastableEntity, SoftDeletableEntity  {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as UnsignedNostrEvent

        if (id != other.id) return false
        if (kind != other.kind) return false
        if (!tags.contentDeepEquals(other.tags)) return false
        if (content != other.content) return false
        if (createdAt != other.createdAt) return false
        if (updatedAt != other.updatedAt) return false
        if (savedAt != other.savedAt) return false
        if (deletedAt != other.deletedAt) return false
        if (broadcastedAt != other.broadcastedAt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + kind
        result = 31 * result + tags.contentDeepHashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + updatedAt.hashCode()
        result = 31 * result + savedAt.hashCode()
        result = 31 * result + (deletedAt?.hashCode() ?: 0)
        result = 31 * result + (broadcastedAt?.hashCode() ?: 0)
        return result
    }


}