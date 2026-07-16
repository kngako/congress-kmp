package press.mantra.compose.database.model

import androidx.room3.Entity
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import press.mantra.compose.database.model.traits.TimestampedEntity
import press.mantra.compose.nostr.nip30303.TranslationChapterEvent
import press.mantra.compose.nostr.nip30303.tags.ChapterIdTag
import press.mantra.compose.nostr.nip30303.tags.TranslationArtifactVersionIdTag
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * 30308
 */
@Entity
data class MantraTranslationChapter(
    val id: HexKey,
    val publicKey: HexKey,
    val chapterId: HexKey,
    val translationArtifactVersionId: HexKey,
    val index: Int,

    val signature: String,

    override val createdAt: Instant = Clock.System.now(),
    override val updatedAt: Instant = createdAt
): TimestampedEntity {
    fun toTranslationChapterEvent(): TranslationChapterEvent {
        return TranslationChapterEvent(
            id = id,
            pubKey = publicKey,
            createdAt = createdAt.epochSeconds,
            tags = TagArrayBuilder<TranslationChapterEvent>()
                .addUnique(
                    TranslationArtifactVersionIdTag.assemble(translationArtifactVersionId)
                )
                .addUnique(
                    ChapterIdTag.assemble(chapterId)
                )
                .build(),
            content = "",
            sig = signature
        )
    }
}

