package press.mantra.compose.nostr.nip30303.tags

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

class ChapterIdTag {
    companion object {
        const val TAG_NAME = "chapterIdTag"

        fun parse(tag: Array<String>): HexKey? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotBlank()) { return null }
            return tag[1]
        }

        fun assemble(chapterIdTag: HexKey) = arrayOf(TAG_NAME, chapterIdTag)
    }
}