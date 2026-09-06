package press.mantra.compose.nostr.nip30303

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.utils.TimeUtils
import press.mantra.compose.nostr.nip30303.tags.ArtifactVersionIdTag
import press.mantra.compose.nostr.nip30303.tags.IndexTag
import press.mantra.compose.nostr.nip30303.tags.NameTag
import press.mantra.compose.nostr.nip30303.tags.OriginalTextTag
import press.mantra.compose.nostr.nip30303.tags.WordStatisticsTag


@Immutable
class ChapterEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {
    val wordStatisticsTag = tags.firstNotNullOfOrNull(WordStatisticsTag::parse)

    fun artifactVersionIdReference() = tags.firstNotNullOfOrNull(ArtifactVersionIdTag::parse)?.ref
    fun artifactVersionId() = artifactVersionIdReference()?.eventId

    fun name() = tags.firstNotNullOfOrNull(NameTag::parse)?.name

    /**
     * The markdown the chapter is, and the only place its chunks come from.
     *
     * Carried on the chapter rather than in an event per paragraph because the
     * group signs the chapter and nothing else. Ids invented locally differ on
     * every device holding the same chapter, so the chunks are split back out
     * of this text when the signed chapter is applied (see
     * MantraChunk.chunksOf), which gives every device the same rows from the
     * same bytes. Signing them alongside the chapter as a batch is possible and
     * was weighed; MantraChunk.chunksOf says what it would cost.
     */
    fun originalText() = tags.firstNotNullOfOrNull(OriginalTextTag::parse)?.originalText

    fun index() = tags.firstNotNullOfOrNull(IndexTag::parse)?.index
    fun wordCount() = wordStatisticsTag?.wordCount
    fun characterCount() = wordStatisticsTag?.characterCount

    companion object {
        const val KIND = 30302
        const val ALT_DESCRIPTION = "Chapter"

        fun build(
            artifactVersionId: String,
            name: String,
            originalText: String,
            index: Int,
            wordCount: Int,
            characterCount: Int,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ChapterEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            alt(ALT_DESCRIPTION)
            addUnique(ArtifactVersionIdTag.assemble(artifactVersionId))
            addUnique(NameTag.assemble(name))
            addUnique(OriginalTextTag.assemble(originalText))
            addUnique(IndexTag.assemble(index))
            addUnique(WordStatisticsTag.assemble(wordCount, characterCount))
            initializer()
        }
    }
}
