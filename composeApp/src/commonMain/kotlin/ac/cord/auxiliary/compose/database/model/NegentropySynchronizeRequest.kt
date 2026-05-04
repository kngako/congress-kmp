package ac.cord.auxiliary.compose.database.model

import ac.cord.auxiliary.compose.database.model.traits.OptionalNostrEventEntity
import ac.cord.auxiliary.compose.database.model.traits.TimestampedEntity
import ac.cord.auxiliary.compose.database.model.types.SynchronizationFilter
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
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
            onDelete = ForeignKey.Companion.CASCADE,
        ),
    ],
    indices = [
        Index("nostrEventId"),
        Index("status"),
    ],
)
data class NegentropySynchronizeRequest(
    @PrimaryKey
    val id: String = Uuid.Companion.generateV4().toHexDashString(),
    val purpose: String,
    val status: String = "pending",
    val relayURL: String,
    val isRecommendedRelay: Boolean = false,

    val level: Int,

    val synchronizationFilter: SynchronizationFilter,

    override val nostrEventId: HexKey? = null,
    override val createdAt: Instant = Clock.System.now(),
    override val updatedAt: Instant = createdAt,
): OptionalNostrEventEntity, TimestampedEntity {

}