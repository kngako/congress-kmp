package press.mantra.compose.database.model

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.PrimaryKey
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import press.mantra.compose.database.model.traits.OptionalNostrEventEntity
import press.mantra.compose.database.model.traits.TimestampedEntity
import press.mantra.compose.nostr.nip30303.ArtifactVersionEvent
import press.mantra.compose.nostr.nip30303.ChapterEvent
import press.mantra.compose.nostr.nip30303.tags.ArtifactVersionIdTag
import press.mantra.compose.nostr.nip30303.tags.ChapterMetadataTag
import press.mantra.compose.nostr.nip30303.tags.IndexTag
import press.mantra.compose.nostr.nip30303.tags.WordStatisticsTag
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * 30302
 */
@Entity(
    foreignKeys = [
        ForeignKey(
            entity = MantraArtifactVersion::class,
            parentColumns = ["id"],
            childColumns = ["artifactVersionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class MantraChapter(
    @PrimaryKey
    val id: HexKey,
    val artifactVersionId: HexKey,
    val publicKey: HexKey,
    val name: String,
    val originalText: String,
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

    fun toChapterEvent(): ChapterEvent {
        return ChapterEvent(
            id = id,
            pubKey = publicKey,
            createdAt = createdAt.epochSeconds,
            tags = TagArrayBuilder<ArtifactVersionEvent>()
                .addUnique(
                    ArtifactVersionIdTag.assemble(artifactVersionId)
                )
                .addUnique(
                    ChapterMetadataTag.assemble(
                        name = name,
                        originalText = originalText,
                    )
                )
                .addUnique(
                    IndexTag.assemble(
                        index
                    )
                )
                .addUnique(
                    WordStatisticsTag.assemble(wordCount, characterCount)
                )
                .build(),
            content = "",
            sig = signature
        )
    }

    companion object {
        fun fromChapterEvent(chapterEvent: ChapterEvent): MantraChapter? {
            return chapterEvent.chapterMetadata?.let { chapterMetadata ->
                chapterEvent.wordStatisticsTag?.let { wordStatisticsTag ->
                    chapterEvent.artifactVersionId()?.let { artifactId ->
                        chapterEvent.index()?.let { index ->
                            MantraChapter(
                                id = chapterEvent.id,
                                publicKey = chapterEvent.pubKey,
                                createdAt = Instant.fromEpochSeconds(chapterEvent.createdAt),
                                artifactVersionId = artifactId,
                                name = chapterMetadata.name,
                                originalText = chapterMetadata.originalText,
                                index = index,
                                wordCount = wordStatisticsTag.wordCount,
                                characterCount = wordStatisticsTag.characterCount,
                                signature = chapterEvent.sig
                            )
                        }
                    }
                }
            }
        }
    }
}
