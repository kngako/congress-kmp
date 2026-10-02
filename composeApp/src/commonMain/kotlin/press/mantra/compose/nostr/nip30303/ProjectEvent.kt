package press.mantra.compose.nostr.nip30303

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.utils.TimeUtils
import press.mantra.compose.nostr.nip30303.tags.ContributorTag
import press.mantra.compose.nostr.nip30303.tags.CreatorTag
import press.mantra.compose.nostr.nip30303.tags.NameTag


/**
 * A body of work: the source versions to translate from and the translations
 * being produced against them, under one name.
 *
 * A project names two kinds of person and they are not the same shape. There is
 * one [creator], so it is a [CreatorTag] -- a single-value tag, like every other
 * field here. There may be several [owners], so each is a [ContributorTag] with
 * [ContributorTag.ROLE_OWNER], which is what that tag is for.
 *
 * Both are written before the event is proposed, so they are inside the id the
 * quorum signs. That makes them the group's statement about whose work this is
 * rather than one device's note about who inserted a row -- and it is a group
 * attestation, not proof of authorship: the creator does not sign this, the room
 * does, so a quorum that signs without reading has credited whoever the proposer
 * typed. `ProposedEvent.summarize` shows both for that reason.
 *
 * Which group a project belongs to is not a tag at all. It is signed by the
 * group's threshold key at the room's derivation path, so it says so by existing.
 */
@Immutable
class ProjectEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig) {

    fun name() = tags.firstNotNullOfOrNull(NameTag::parse)?.name

    fun creator() = tags.firstNotNullOfOrNull(CreatorTag::parse)?.pubKey

    /**
     * Everyone this project belongs to, in the order the event carries them.
     *
     * Filtered on the role rather than taking every `p` tag. Every contributor
     * tag on a project is an owner today, and that is one future role away from
     * being false -- at which point an unfiltered read would quietly hand back an
     * owner list with somebody else in it.
     *
     * Empty is not a project this app wrote: [build] refuses it. It is what a
     * reader sees when [ContributorTag.ROLE_OWNER] is missing from that tag's
     * known roles, since the role then parses as null -- so treat empty as that
     * bug rather than as a project nobody owns.
     */
    fun owners() = tags.mapNotNull(ContributorTag::parse)
        .filter { it.role == ContributorTag.ROLE_OWNER }
        .map { it.pubKey }

    companion object {
        const val KIND = 30330
        const val ALT_DESCRIPTION = "Project"

        /**
         * [ownerPublicKeys] is a set and must not be empty.
         *
         * The caller always has an answer -- the room's own key, when the group
         * owns what it makes -- and an empty set would make "owned by us" and
         * "owner unstated" the same event, which leaves every reader needing to
         * know the default.
         */
        fun build(
            name: String,
            description: String,
            creatorPublicKey: HexKey,
            ownerPublicKeys: Set<HexKey>,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<ProjectEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, description, createdAt) {
            require(ownerPublicKeys.isNotEmpty()) { "A project has at least one owner" }

            alt(ALT_DESCRIPTION)
            addUnique(NameTag.assemble(name))
            addUnique(CreatorTag.assemble(creatorPublicKey))

            // Sorted, because the id is a hash over the tags as emitted and a
            // multi-select hands its selection over in no particular order --
            // the same reason DkgSession.participantPublicKeys sorts.
            //
            // addUniqueValueIfNew, never addUnique: addUnique keeps one tag per
            // *name*, so three owners would come out as one and the loss is
            // silent. Deduping on the pubkey is the right thing for a set.
            ownerPublicKeys.sorted().forEach { ownerPublicKey ->
                addUniqueValueIfNew(
                    ContributorTag.assemble(ownerPublicKey, ContributorTag.ROLE_OWNER)
                )
            }

            initializer()
        }
    }
}
