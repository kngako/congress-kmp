package ac.aux.compose.database.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Kind
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import kotlin.time.Clock
import kotlin.time.Instant

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = Profile::class,
            parentColumns = ["publicKey"],
            childColumns = ["profilePublicKey"],
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
            childColumns = ["replyToId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("profilePublicKey"),
        Index("nostrEventId"),
        Index("replyToId")
    ],
)
data class Reaction(
    @PrimaryKey
    val id: HexKey,
    val nostrEventId: HexKey, // Might be a replaceable nostr event?
    val replyToId: HexKey,
    val profilePublicKey: HexKey,
    val createdAt: Instant,
    val content: String,
)
