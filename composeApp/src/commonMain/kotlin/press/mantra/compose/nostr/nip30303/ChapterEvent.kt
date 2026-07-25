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
