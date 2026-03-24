package ac.aux.compose.database.model

import ac.aux.compose.database.model.traits.BroadcastableEntity
import ac.aux.compose.database.model.traits.LocalStoreEntity
import ac.aux.compose.database.model.traits.SoftDeletableEntity
import ac.aux.compose.database.model.traits.TimestampedEntity
import ac.aux.compose.database.model.traits.UserViewableEntity
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Kind
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import kotlin.time.Clock
import kotlin.time.Instant

@Entity
data class NostrEvent(
    @PrimaryKey
    val id: String,
    val pubKey: HexKey,
    val kind: Kind,
    val tags: TagArray,
    val content: String,
    val sig: HexKey,

    override val createdAt: Instant = Clock.System.now(),
    override val updatedAt: Instant = Clock.System.now(),
    override val savedAt: Instant = Clock.System.now(),
    override val deletedAt: Instant? = null,
    override val broadcastedAt: Instant? = null,
): TimestampedEntity, LocalStoreEntity, BroadcastableEntity, SoftDeletableEntity  {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as NostrEvent

        if (kind != other.kind) return false
        if (id != other.id) return false
        if (pubKey != other.pubKey) return false
        if (createdAt != other.createdAt) return false
        if (!tags.contentDeepEquals(other.tags)) return false
        if (content != other.content) return false
        if (sig != other.sig) return false

        return true
    }

    override fun hashCode(): Int {
        var result = kind
        result = 31 * result + id.hashCode()
        result = 31 * result + pubKey.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + tags.contentDeepHashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + sig.hashCode()
        return result
    }
}