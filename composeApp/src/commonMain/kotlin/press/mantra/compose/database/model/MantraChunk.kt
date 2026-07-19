package press.mantra.compose.database.model

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.PrimaryKey
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import press.mantra.compose.database.model.traits.OptionalNostrEventEntity
import press.mantra.compose.database.model.traits.TimestampedEntity
import press.mantra.compose.nostr.nip30303.ChunkEvent
import press.mantra.compose.nostr.nip30303.tags.ChapterIdTag
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * 30303
 */
@Entity(
    foreignKeys = [
        ForeignKey(
            entity = MantraChapter::class,
            parentColumns = ["id"],
            childColumns = ["chapterId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class MantraChunk(
    @PrimaryKey
    val id: HexKey,
    val chapterId: HexKey,
    val publicKey: HexKey,
    val text: String,
    val index: Int,
    val wordCount: Int,
    val characterCount: Int,

    /**
     * Signature will be an empty string if this is a rumor
     */
    val signature: String,

    val relayHint: String? = null,
    val marmotGroupEventId: HexKey? = null,

    /**
     * Related nostrEventId if this is not a rumor...
     */
    override val nostrEventId: HexKey? = null,
    override val createdAt: Instant = Clock.System.now(),
    override val updatedAt: Instant = createdAt
): OptionalNostrEventEntity, TimestampedEntity {

    fun toChunkEvent(): ChunkEvent {
        return ChunkEvent(
            id = id,
            pubKey = publicKey,
            createdAt = createdAt.epochSeconds,
            tags = TagArrayBuilder<ChunkEvent>().addUnique(
                ChapterIdTag.assemble(chapterId)
            ).build(),
            content = "",
            sig = signature
        )
    }
}
