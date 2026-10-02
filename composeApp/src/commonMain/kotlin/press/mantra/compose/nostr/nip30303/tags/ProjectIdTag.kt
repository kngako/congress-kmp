package press.mantra.compose.nostr.nip30303.tags

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.tags.events.EventReference
import com.vitorpamplona.quartz.utils.arrayOfNotNull
import com.vitorpamplona.quartz.utils.ensure

data class ProjectIdTag(
    val ref: EventReference,
) {
    constructor(eventId: String, relayHint: NormalizedRelayUrl?, pubkey: String?) : this(
        EventReference(eventId, pubkey, relayHint)
    )

    companion object {
        // Plain, like `artifactId`. Six tags in this package carry a stray `Tag`
        // suffix in the wire value -- `chapterIdTag` and friends -- which is a
        // copy-paste slip and is unfixable: those strings are inside event ids
        // the group has signed, so renaming one invalidates every signature over
        // it. Do not copy it and do not tidy it.
        const val TAG_NAME = "projectId"

        fun parse(tag: Array<String>): ProjectIdTag? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].length == 64) { return null }

            val relayHint = tag.getOrNull(2)?.let { RelayUrlNormalizer.normalizeOrNull(it) }

            return ProjectIdTag(tag[1], relayHint, tag.getOrNull(3))
        }

        fun assemble(
            eventId: HexKey,
            relay: NormalizedRelayUrl? = null,
            pubkey: String? = null,
        ) = arrayOfNotNull(TAG_NAME, eventId, relay?.url, pubkey)

        fun assemble(ref: EventReference) = assemble(ref.eventId, ref.relayHint, ref.author)
    }
}
