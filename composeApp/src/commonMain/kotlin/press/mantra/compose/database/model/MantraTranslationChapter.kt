package press.mantra.compose.database.model

import androidx.room3.Entity
import androidx.room3.ForeignKey
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import press.mantra.compose.database.model.traits.OptionalNostrEventEntity
import press.mantra.compose.database.model.traits.TimestampedEntity
import press.mantra.compose.nostr.nip30303.TranslationChapterEvent
import press.mantra.compose.nostr.nip30303.tags.ChapterIdTag
import press.mantra.compose.nostr.nip30303.tags.TranslationArtifactVersionIdTag
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * 30308
 */
@Entity(
    foreignKeys = [
        ForeignKey(
            entity = MantraTranslationArtifactVersion::class,
            parentColumns = ["id"],
            childColumns = ["translationArtifactVersionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class MantraTranslationChapter(
    val id: HexKey,
    val publicKey: HexKey,
    val chapterId: HexKey,
    val translationArtifactVersionId: HexKey,
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

