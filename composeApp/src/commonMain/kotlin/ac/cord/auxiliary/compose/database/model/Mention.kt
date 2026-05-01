package ac.cord.auxiliary.compose.database.model

import ac.cord.auxiliary.compose.database.model.traits.BroadcastableEntity
import ac.cord.auxiliary.compose.database.model.traits.LocalStoreEntity
import ac.cord.auxiliary.compose.database.model.traits.NostrEventEntity
import ac.cord.auxiliary.compose.database.model.traits.SoftDeletableEntity
import ac.cord.auxiliary.compose.database.model.traits.TimestampedEntity
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.PrimaryKey
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlin.time.Clock
import kotlin.time.Instant

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = Profile::class,
            parentColumns = ["publicKey"],
            childColumns = ["mentionedPublicKey"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = NostrEvent::class,
            parentColumns = ["id"],
            childColumns = ["mentioningNostrEventId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ]
)
data class Mention(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val mentionedPublicKey: HexKey,
    val mentioningNostrEventId: HexKey? = null,

    val relayUrl: String? = null,

    override val createdAt: Instant = Clock.System.now(),
    override val updatedAt: Instant = Clock.System.now(),
    override val savedAt: Instant = Clock.System.now(),
    override val deletedAt: Instant? = null,
    override val broadcastedAt: Instant? = null,
): TimestampedEntity, LocalStoreEntity, BroadcastableEntity, SoftDeletableEntity
