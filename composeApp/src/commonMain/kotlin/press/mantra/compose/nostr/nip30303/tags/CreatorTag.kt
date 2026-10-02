package press.mantra.compose.nostr.nip30303.tags

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

/**
 * Whoever started the thing this tag is on.
 *
 * A tag of its own rather than a [ContributorTag] with a creator role, because
 * there is exactly one creator and a single-valued field is what every other tag
 * in this package is. The owners are the other shape -- a set, open-ended, and so
 * [ContributorTag] with [ContributorTag.ROLE_OWNER].
 *
 * The name is multi-character on purpose. NIP-01 indexes single-letter tag names
 * and makes them queryable, so a `p` tag is an address: relays index it and a
 * client may read it as a mention. These events live inside a Marmot group and
 * are never on a relay as themselves, but `BroadcastGroupSignedEventScreen` can
 * put one there, and a creator should travel in that as data rather than as a
 * recipient.
 */
class CreatorTag(
    val pubKey: HexKey,
) {
    fun toTagArray() = assemble(pubKey = pubKey)

    companion object {
        const val TAG_NAME = "creator"

        fun parse(tag: Array<String>): CreatorTag? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            // Without this a malformed value parses, lands on the row, and names
            // nobody. ContributorTag checks the same thing for the same reason.
            ensure(tag[1].length == 64) { return null }

            return CreatorTag(pubKey = tag[1])
        }

        fun assemble(pubKey: HexKey): Array<String> = arrayOf(TAG_NAME, pubKey)

        fun assemble(creatorTag: CreatorTag) = assemble(pubKey = creatorTag.pubKey)
    }
}
