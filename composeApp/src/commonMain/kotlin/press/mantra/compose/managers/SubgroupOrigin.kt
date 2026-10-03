package press.mantra.compose.managers

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Kind
import press.mantra.compose.database.model.traits.SubgroupOriginEntity

/**
 * What a subgroup was made for, and everything a picker needs to start one.
 *
 * Making a room for a dialect, an artifact, a translation or a project takes
 * three facts, and all three are already sitting on the row the user was looking
 * at when they asked: the room it lives in is the parent, its name is the name to
 * propose, and its id and kind are what the parent's quorum certifies the new
 * room is for. [of] reads them, and that is the whole of the convenience.
 *
 * Pure, and parallel to `TranslationScaffold` and `ChapterScaffolding` -- no
 * database, no Compose, no repository. Nothing here opens a ceremony: the picker
 * still does that through `DkgRepository.proposeRitual`, and every refusal is
 * still `SubgroupManager.refuseSubgroup`'s. What this removes is a user retyping
 * a name the app already knew and a room ending up related to its entity by
 * having been named after it.
 *
 * See `docs/subgroups-for-entities.md`.
 */
object SubgroupOrigin {
    /**
     * The entity a subgroup is for: a kind and an id, together or not at all.
     *
     * There is no constructor that holds one of the two, which is the point of
     * the type. An id with no kind is unresolvable without trying four tables; a
     * kind with no id names a category rather than a thing. `SubgroupOriginTag`
     * refuses to parse half of this for the same reason.
     *
     * Holding one is not evidence of anything. The parent's quorum signs that it
     * *intends* a room to be for this entity; it does not vouch that the entity
     * exists, and a device that holds the certificate routinely holds none of the
     * parent's rows -- a subgroup's members need never have been in the parent.
     */
    data class Ref(
        val kind: Kind,
        val id: HexKey,
    )

    /**
     * Everything the subgroup picker needs, read off [entity].
     *
     * [Candidate.name] is bare -- no `#`. The convention is applied by
     * `MarmotGroupName.of` where the name is actually used, twice and
     * idempotently: once into the birth certificate the parent's quorum signs and
     * once onto the room itself. Normalising here as well would be a third place
     * that has to agree, and the one it would most easily disagree with is the
     * name field the user is still typing in.
     *
     * A blank name comes back blank rather than becoming "a group" or the
     * entity's id. The picker already refuses to confirm without a name -- "Give
     * the subgroup a name." -- and inventing one here would route around a
     * refusal whose whole job is to be hit.
     */
    fun of(entity: SubgroupOriginEntity): Candidate = Candidate(
        parentChatRoomId = entity.chatRoomId,
        name = entity.name.trim(),
        origin = Ref(kind = entity.subgroupOriginKind, id = entity.id),
    )

    /**
     * A subgroup that could be made, before anybody has picked who runs it.
     *
     * Deliberately not a proposal and not a session. Who the admins are and what
     * the threshold is are settled in the picker and nowhere else -- ChillDKG
     * hashes the threshold into the session identity, so there is no later screen
     * where it could change -- and this type stops short of all of it.
     */
    data class Candidate(
        val parentChatRoomId: String,
        val name: String,
        val origin: Ref,
    )
}
