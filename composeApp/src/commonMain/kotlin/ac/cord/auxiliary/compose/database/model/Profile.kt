package ac.cord.auxiliary.compose.database.model

import ac.cord.auxiliary.compose.database.model.traits.BroadcastableEntity
import ac.cord.auxiliary.compose.database.model.traits.LocalStoreEntity
import ac.cord.auxiliary.compose.database.model.traits.NostrEventEntity
import ac.cord.auxiliary.compose.database.model.traits.SoftDeletableEntity
import ac.cord.auxiliary.compose.database.model.traits.TimestampedEntity
import ac.cord.auxiliary.compose.database.model.traits.UserViewableEntity
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.time.Clock
import kotlin.time.Instant

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = NostrEvent::class,
            parentColumns = ["id"],
            childColumns = ["nostrEventId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index("nostrEventId"),
    ],
)

data class Profile(
    @PrimaryKey
    val publicKey: String,
    val userName: String? = null, // name in the nostr event...
    val displayName: String? = null,
    val picture: String? = null,
    val banner: String? = null,
    val website: String? = null,
    val about: String? = null,
    val bot: Boolean? = null,
    val pronouns: String? = null,
    val nip05: String? = null,
    val domain: String? = null,
    val lud06: String? = null,
    val lud16: String? = null,
    val twitter: String? = null,

    override val nostrEventId: String,

    override val createdAt: Instant = Clock.System.now(),
    override val updatedAt: Instant = Clock.System.now(),
    override val savedAt: Instant = Clock.System.now(),
    override val viewedAt: Instant? = null,
    override val deletedAt: Instant? = null,
    override val broadcastedAt: Instant? = null,
): NostrEventEntity, TimestampedEntity, LocalStoreEntity, UserViewableEntity, BroadcastableEntity, SoftDeletableEntity {
    fun humanReadableNameOrPubkey(): String {
        return displayName ?: userName ?: nip05 ?: twitter ?: publicKey
    }
}