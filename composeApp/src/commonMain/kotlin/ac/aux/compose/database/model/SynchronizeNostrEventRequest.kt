package ac.aux.compose.database.model

import ac.aux.compose.database.model.traits.OptionalNostrEventEntity
import ac.aux.compose.database.model.traits.TimestampedEntity
import ac.aux.compose.database.model.traits.UnsignedNostrEventEntity
import ac.aux.compose.database.model.typealiases.HexArray
import ac.aux.compose.database.model.typealiases.KindArray
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Entity(
    foreignKeys = [
        ForeignKey(
            entity = NostrEvent::class,
            parentColumns = ["id"],
            childColumns = ["nostrEventId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = UnsignedNostrEvent::class,
            parentColumns = ["id"],
            childColumns = ["unsignedNostrEventId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("nostrEventId"),
        Index("status"),
    ],
)
data class SynchronizeNostrEventRequest(
    @PrimaryKey
    val id: String = Uuid.generateV4().toHexDashString(),
    val status: String = "pending",
    val relayURL: String,
    val eventIds: HexArray? = null,
    val authorPublicKeys: HexArray? = null, // TODO: PublicKeyArray
    val kinds: KindArray? = null, // TODO: KindArray
    val tagName: String? = null,
    val since: Instant? = null,
    val until: Instant? = null,
    val limit: Int? = null,
    val search: String? = null,

    override val nostrEventId: HexKey? = null,
    override val unsignedNostrEventId: Long? = null,
    override val createdAt: Instant = Clock.System.now(),
    override val updatedAt: Instant = createdAt,
): OptionalNostrEventEntity, UnsignedNostrEventEntity, TimestampedEntity {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as SynchronizeNostrEventRequest

        if (limit != other.limit) return false
        if (unsignedNostrEventId != other.unsignedNostrEventId) return false
        if (id != other.id) return false
        if (status != other.status) return false
        if (relayURL != other.relayURL) return false
        if (!eventIds.contentEquals(other.eventIds)) return false
        if (!authorPublicKeys.contentEquals(other.authorPublicKeys)) return false
        if (!kinds.contentEquals(other.kinds)) return false
        if (tagName != other.tagName) return false
        if (since != other.since) return false
        if (until != other.until) return false
        if (nostrEventId != other.nostrEventId) return false
        if (createdAt != other.createdAt) return false
        if (updatedAt != other.updatedAt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = limit?.hashCode() ?: 0
        result = 31 * result + (unsignedNostrEventId?.hashCode() ?: 0)
        result = 31 * result + id.hashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + relayURL.hashCode()
        result = 31 * result + (eventIds?.contentHashCode() ?: 0)
        result = 31 * result + (authorPublicKeys?.contentHashCode() ?: 0)
        result = 31 * result + (kinds?.contentHashCode() ?: 0)
        result = 31 * result + (tagName?.hashCode() ?: 0)
        result = 31 * result + (since?.hashCode() ?: 0)
        result = 31 * result + (until?.hashCode() ?: 0)
        result = 31 * result + (nostrEventId?.hashCode() ?: 0)
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + updatedAt.hashCode()
        return result
    }
}