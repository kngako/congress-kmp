package ac.aux.compose.database.model

import ac.aux.compose.database.model.traits.NostrEventEntity
import ac.aux.compose.database.model.traits.OptionalNostrEventEntity
import ac.aux.compose.database.model.traits.TimestampedEntity
import ac.aux.compose.database.model.traits.UnsignedNostrEventEntity
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
    val eventIds: String? = null, // TODO: EventIdArray
    val authorPublicKeys: String? = null, // TODO: PublicKeyArray
    val kinds: String? = null, // TODO: KindArray
    val tagName: String? = null,
    val since: Instant? = null,
    val until: Instant? = null,
    val limit: Long? = null,

    override val nostrEventId: HexKey? = null,
    override val unsignedNostrEventId: Long? = null,
    override val createdAt: Instant = Clock.System.now(),
    override val updatedAt: Instant = createdAt,
): OptionalNostrEventEntity, UnsignedNostrEventEntity, TimestampedEntity