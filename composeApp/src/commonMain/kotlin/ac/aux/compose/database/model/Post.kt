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
        // Support reposts
        ForeignKey(
            entity = Post::class,
            parentColumns = ["id"],
            childColumns = ["repostId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("profilePublicKey"),
        Index("nostrEventId"),
        Index("replyToId"),
        Index("repostId")
    ],
)
data class Post(
    @PrimaryKey
    val id: HexKey,
    val nostrEventId: HexKey, // Might be a replaceable nostr event?
    val replyToId: HexKey? = null,
    val repostId: HexKey? = null,
    val quote: HexKey? = null,
    val profilePublicKey: HexKey,
    val createdAt: Instant,
    val savedAt: Instant,
    val broadcastedAt: Instant,
    val content: String,
) {
    companion object {
        val DUMMY_POSTS = arrayOf(
            Post(
                id = "genesisnoteid",
                nostrEventId = "genesisnoteid", // Need to check how to handle addressableEvents...
                profilePublicKey = "satoshi",
                createdAt = Clock.System.now(),
                savedAt = Clock.System.now(),
                broadcastedAt = Clock.System.now(),
                content = "Chancellor the rapper...",
            )
        )
    }
}
