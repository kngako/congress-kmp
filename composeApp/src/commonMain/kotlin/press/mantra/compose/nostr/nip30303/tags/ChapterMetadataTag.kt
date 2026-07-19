package press.mantra.compose.nostr.nip30303.tags

import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

class ChapterMetadataTag(
    val name: String,
    val originalText: String,
) {
    fun toTagArray() = assemble(
        name = name,
        originalText = originalText,
    )

    companion object {
        const val TAG_NAME = "chapter"

        fun parse(tag: Array<String>): ChapterMetadataTag? {
            ensure(tag.has(2)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }

            return ChapterMetadataTag(
                name = tag[1],
                originalText = tag[2],
            )
        }

        fun assemble(
            name: String,
            originalText: String,
        ): Array<String> = arrayOf(
            TAG_NAME,
            name,
            originalText,
        )

        fun assemble(chapterMetadataTag: ChapterMetadataTag) = assemble(
            name = chapterMetadataTag.name,
            originalText = chapterMetadataTag.originalText,
        )
    }
}