package press.mantra.compose.database.model

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import press.mantra.compose.database.model.traits.TimestampedEntity
import press.mantra.compose.nostr.nip30303.ChunkEvent
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * 30303
 */
@Entity
data class MantraChunk(
    @PrimaryKey
    val id: HexKey,
    val chapterId: HexKey,
    val publicKey: HexKey,
    val text: String,
    val index: Int,
    val wordCount: Int,
    val characterCount: Int,

    val signature: String,

    override val createdAt: Instant = Clock.System.now(),
    override val updatedAt: Instant = createdAt
): TimestampedEntity {

    fun toChunkEvent(): ChunkEvent {
        return ChunkEvent(
            id = id,
            pubKey = publicKey,
            createdAt = createdAt.epochSeconds,
            tags = emptyArray(),
            content = "",
            sig = signature
        )
    }
}
