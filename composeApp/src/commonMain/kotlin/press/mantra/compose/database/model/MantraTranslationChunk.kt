package press.mantra.compose.database.model

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import press.mantra.compose.database.model.traits.TimestampedEntity
import press.mantra.compose.nostr.nip30303.TranslationChunkEvent
import press.mantra.compose.nostr.nip30303.tags.ChunkIdTag
import press.mantra.compose.nostr.nip30303.tags.TranslationChapterIdTag
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * 30311
 */
@Entity
data class MantraTranslationChunk(
    @PrimaryKey
    val id: HexKey,
    val publicKey: HexKey,
    val chunkId: HexKey,
    val translationChapterId: HexKey,
    val text: String,
    val index: Int,

    val signature: String,

    override val createdAt: Instant = Clock.System.now(),
    override val updatedAt: Instant = createdAt
): TimestampedEntity {

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
                    ChunkIdTag.assemble(chunkId) // TODO: PublicKeyListTag
                )
                .build(),
            content = "",
            sig = signature
        )
    }
}
