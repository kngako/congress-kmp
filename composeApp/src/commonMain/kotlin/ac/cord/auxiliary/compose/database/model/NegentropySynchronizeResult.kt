package ac.cord.auxiliary.compose.database.model

import ac.cord.auxiliary.compose.database.model.traits.OptionalNostrEventEntity
import ac.cord.auxiliary.compose.database.model.traits.TimestampedEntity
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
            entity = SynchronizeNostrEventResult::class,
            parentColumns = ["id"],
            childColumns = ["negentropySynchronizeRequestId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class NegentropySynchronizeResult(
    @PrimaryKey
    val id: String = Uuid.generateV4().toHexDashString(),
    val relayURL: String,
    val status: String = "pending",

    val negentropySynchronizeRequestId: String,

    override val createdAt: Instant = Clock.System.now(),
    override val updatedAt: Instant = createdAt,
): TimestampedEntity