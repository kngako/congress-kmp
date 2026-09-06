package press.mantra.compose.nostr.archive.tags

import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

/**
 * Ties the pages of one archive together.
 *
 * An archive is more than one event and its pages arrive in no particular
 * order, so a receiver has to be able to tell page 2 of this archive from page 2
 * of somebody else's. Two members answering the same request is the ordinary
 * case rather than the odd one -- nothing stops them, and nothing should, since
 * an incomplete answer is exactly what a second answer fixes -- and without an
 * id their pages would interleave into one sequence that is neither.
 *
 * Fresh random bytes per archive, not derived from anything. Two members
 * assembling the same rows must not collide on an id, because their page counts
 * will differ whenever their databases do.
 */
class ArchiveIdTag(
    val archiveId: String,
) {
    fun toTagArray() = assemble(archiveId = archiveId)

    companion object {
        const val TAG_NAME = "archiveId"

        fun parse(tag: Array<String>): ArchiveIdTag? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotBlank()) { return null }

            return ArchiveIdTag(archiveId = tag[1])
        }

        fun assemble(archiveId: String): Array<String> = arrayOf(TAG_NAME, archiveId)

        fun assemble(archiveIdTag: ArchiveIdTag) = assemble(archiveId = archiveIdTag.archiveId)
    }
}
