package ac.cord.auxiliary.compose.database.model

import ac.cord.auxiliary.compose.database.model.traits.BroadcastableEntity
import ac.cord.auxiliary.compose.database.model.traits.LocalStoreEntity
import ac.cord.auxiliary.compose.database.model.traits.SoftDeletableEntity
import ac.cord.auxiliary.compose.database.model.traits.TimestampedEntity
import ac.cord.auxiliary.compose.database.model.traits.UserViewableEntity
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
            childColumns = ["inReplyToProfilePublicKey"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Profile::class,
            parentColumns = ["publicKey"],
            childColumns = ["inReplyToRootProfilePublicKey"],
            onDelete = ForeignKey.CASCADE,
        ),
        // This might cause problems... but we gonna make the feed so cool if it works...
        ForeignKey(
            entity = NostrEvent::class,
            parentColumns = ["id"],
            childColumns = ["replyingNostrEventId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = NostrEvent::class,
            parentColumns = ["id"],
            childColumns = ["inReplyToNostrEventId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = NostrEvent::class,
            parentColumns = ["id"],
            childColumns = ["inReplyToRootNostrEventId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    primaryKeys = ["replyingNostrEventId", "inReplyToNostrEventId"]
)
data class InReplyToRelation(
    val replyingNostrEventId: HexKey, // Might be a replaceable nostr event?

    val inReplyToNostrEventId: HexKey,
    val inReplyToProfilePublicKey: HexKey?,

    val inReplyToRootNostrEventId: HexKey?,
    val inReplyToRootProfilePublicKey: HexKey?,

    override val createdAt: Instant = Clock.System.now(),
    override val updatedAt: Instant = Clock.System.now(),
    override val savedAt: Instant = Clock.System.now(),
    override val viewedAt: Instant? = null,
    override val deletedAt: Instant? = null,
): TimestampedEntity, LocalStoreEntity, UserViewableEntity, SoftDeletableEntity {

}