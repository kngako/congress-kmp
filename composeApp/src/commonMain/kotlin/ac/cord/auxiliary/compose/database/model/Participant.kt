package ac.cord.auxiliary.compose.database.model

import ac.cord.auxiliary.compose.database.model.traits.BroadcastableEntity
import ac.cord.auxiliary.compose.database.model.traits.LocalStoreEntity
import ac.cord.auxiliary.compose.database.model.traits.SoftDeletableEntity
import ac.cord.auxiliary.compose.database.model.traits.TimestampedEntity
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.PrimaryKey
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Derived from kind14/15 tags
 * [
 *     ["p", "<receiver-1-pubkey>", "<relay-url>"],
 *     ["p", "<receiver-2-pubkey>", "<relay-url>"],
 *     // rest of tags...
 *   ]
 *
 *   TODO: Might want to add a relation for this...
 */
@Entity(
    foreignKeys = [
        ForeignKey(
            entity = Profile::class,
            parentColumns = ["publicKey"],
            childColumns = ["participantPublicKey"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ChatRoom::class,
            parentColumns = ["id"],
            childColumns = ["chatRoomId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ]
)
data class Participant(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val participantPublicKey: HexKey,
    val chatRoomId: String,

    override val createdAt: Instant = Clock.System.now(),
    override val updatedAt: Instant = Clock.System.now(),
    override val savedAt: Instant = Clock.System.now(),
    override val deletedAt: Instant? = null,
    override val broadcastedAt: Instant? = null,
): TimestampedEntity, LocalStoreEntity, BroadcastableEntity, SoftDeletableEntity
