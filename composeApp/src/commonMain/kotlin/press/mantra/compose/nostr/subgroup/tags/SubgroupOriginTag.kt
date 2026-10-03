package press.mantra.compose.nostr.subgroup.tags

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Kind
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

/**
 * What a subgroup was made for: a kind, and the id of the event.
 *
 * A group's room holds dialects, artifacts, translations and projects, and a
 * subgroup is often made for exactly one of them -- the room where that
 * translation gets worked on. This is the tag that says so, and it is the only
 * place the link exists: a room's name is not an identity, and a local column
 * would be a fact one device holds.
 *
 * ### Both halves or neither
 *
 * [parse] refuses a tag with only one value. An id alone would have to be looked
 * up in four tables to be understood, and a reader without the kind cannot tell
 * "a project this device does not hold" from "not a project at all". A kind
 * alone names a category rather than a thing. `SubgroupOrigin.Ref` has no
 * constructor for half of this either, which is the same rule stated where a
 * caller would otherwise assemble one by hand.
 *
 * The pair, and the order, follow `PayloadKindTag`/`PayloadIdTag`: a client
 * deciding what it is looking at before parsing the thing itself.
 *
 * ### It authenticates nothing about the entity
 *
 * On a certificate the parent's quorum is signing that *it intends* the room to
 * be for that entity. It is not vouching that the entity exists, that this
 * device can resolve it, or that the entity's own signature is good -- which is
 * the limit `docs/project-feature.md` states for a cross-room project
 * membership, and it is the right limit here for the same reason. A subgroup's
 * members need never have been in the parent, so a certificate naming a row they
 * will never hold is the normal case rather than an error.
 *
 * On a ritual proposal it authenticates even less: anybody can put any id there.
 * See `DkgRitualEvents.parseOrigin`.
 *
 * ### `certifies` never looks at it
 *
 * Deliberately. The name and the `p` tags on a certificate are the *founding*
 * roster -- signed once, never compared against anything current -- and the
 * origin joins them. A certificate with no origin at all is valid, which it has
 * to be: every certificate signed before this feature has none.
 */
class SubgroupOriginTag(
    val kind: Kind,
    val id: HexKey,
) {
    fun toTagArray() = assemble(kind = kind, id = id)

    companion object {
        const val TAG_NAME = "subgroupFor"

        fun parse(tag: Array<String>): SubgroupOriginTag? {
            ensure(tag.has(2)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }

            val kind = tag[1].toIntOrNull() ?: return null

            // The length check the nip30303 id tags make. An id of the wrong
            // shape is not a thing to carry forward in the hope that something
            // downstream refuses it -- nothing downstream would, since nothing
            // resolves an origin before showing it.
            ensure(tag[2].length == 64) { return null }

            return SubgroupOriginTag(kind = kind, id = tag[2])
        }

        fun assemble(kind: Kind, id: HexKey): Array<String> =
            arrayOf(TAG_NAME, kind.toString(), id)

        fun assemble(subgroupOriginTag: SubgroupOriginTag) =
            assemble(kind = subgroupOriginTag.kind, id = subgroupOriginTag.id)
    }
}
