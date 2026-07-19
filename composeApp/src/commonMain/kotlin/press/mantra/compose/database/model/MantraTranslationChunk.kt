package press.mantra.compose.database.model

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.PrimaryKey
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import press.mantra.compose.database.model.traits.OptionalNostrEventEntity
import press.mantra.compose.database.model.traits.TimestampedEntity
import press.mantra.compose.nostr.nip30303.ChunkEvent
import press.mantra.compose.nostr.nip30303.TranslationChunkEvent
import press.mantra.compose.nostr.nip30303.tags.ChunkIdTag
import press.mantra.compose.nostr.nip30303.tags.IndexTag
import press.mantra.compose.nostr.nip30303.tags.TranslationChapterIdTag
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * 30311
 */
@Entity(
    foreignKeys = [
        ForeignKey(
            entity = MantraChunk::class,
            parentColumns = ["id"],
            childColumns = ["chunkId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MantraTranslationChapter::class,
            parentColumns = ["id"],
            childColumns = ["translationChapterId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class MantraTranslationChunk(
    @PrimaryKey
    val id: HexKey,
    val publicKey: HexKey,
    val chunkId: HexKey,
    val translationChapterId: HexKey,
    val text: String,
    val index: Int,

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

    fun toTranslationChunkEvent(): TranslationChunkEvent {
        return TranslationChunkEvent(
            id = id,
            pubKey = publicKey,
            createdAt = createdAt.epochSeconds,
            tags = TagArrayBuilder<TranslationChunkEvent>()
                .addUnique(
                    TranslationChapterIdTag.assemble(translationChapterId)
                )
                .addUnique(
                    ChunkIdTag.assemble(chunkId)
                )
                .addUnique(
                    IndexTag.assemble(index)
                )
                .build(),
            content = text,
            sig = signature
        )
    }

    companion object {
        fun fromTranslationChunkEvent(translationChunkEvent: TranslationChunkEvent): MantraTranslationChunk? {
            return translationChunkEvent.translationChapterId()?.let { translationChapterId ->
                translationChunkEvent.chunkId()?.let { chunkId ->
                    translationChunkEvent.index()?.let { index ->
                        MantraTranslationChunk(
                            id = translationChunkEvent.id,
                            publicKey = translationChunkEvent.pubKey,
                            createdAt = Instant.fromEpochSeconds(translationChunkEvent.createdAt),
                            translationChapterId = translationChapterId,
                            chunkId = chunkId,
                            index = index,
                            text = translationChunkEvent.content,
                            signature = translationChunkEvent.sig
                        )
                    }
                }
            }
        }
    }
}
