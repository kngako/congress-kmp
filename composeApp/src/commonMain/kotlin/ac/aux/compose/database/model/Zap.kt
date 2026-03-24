package ac.aux.compose.database.model

import ac.aux.compose.database.model.traits.BroadcastableEntity
import ac.aux.compose.database.model.traits.LocalStoreEntity
import ac.aux.compose.database.model.traits.NostrEventEntity
import ac.aux.compose.database.model.traits.SoftDeletableEntity
import ac.aux.compose.database.model.traits.TimestampedEntity
import ac.aux.compose.database.model.traits.UserViewableEntity
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlin.time.Clock
import kotlin.time.Instant

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = Profile::class,
            parentColumns = ["publicKey"],
            childColumns = ["senderPublicKey"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Profile::class,
            parentColumns = ["publicKey"],
            childColumns = ["receiverPublicKey"],
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
            childColumns = ["postId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("senderPublicKey"),
        Index("nostrEventId"),
        Index("postId")
    ],
)
data class Zap(
    @PrimaryKey
    val id: HexKey,
    val postId: HexKey,
    val senderPublicKey: HexKey,
    val receiverPublicKey: HexKey,
    val message: String?,
    val invoice: String?,
    val amountInMillisatoshis: Long?,

    override val nostrEventId: HexKey, // Might be a replaceable nostr event?

    override val createdAt: Instant = Clock.System.now(),
    override val updatedAt: Instant = Clock.System.now(),
    override val savedAt: Instant = Clock.System.now(),
    override val viewedAt: Instant? = null,
    override val deletedAt: Instant? = null,
    override val broadcastedAt: Instant? = null,
) : NostrEventEntity, TimestampedEntity, LocalStoreEntity, UserViewableEntity, BroadcastableEntity, SoftDeletableEntity
