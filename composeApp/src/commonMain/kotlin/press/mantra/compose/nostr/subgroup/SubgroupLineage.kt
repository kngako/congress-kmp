package press.mantra.compose.nostr.subgroup

import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * Every group a room is a child of, each with the signature saying so.
 *
 * A room's lineage is a *set*, and this is the type that makes it one. A group
 * is founded with one parent and may be adopted by another later -- see
 * `docs/subgroups-multiple-parents.md` -- so "the parent" was never a field so
 * much as a set that had only ever held one thing.
 *
 * ### Why a type rather than a list
 *
 * Two invariants have to hold wherever a lineage is written, and neither is the
 * sort a caller can be trusted to remember.
 *
 * **No parent appears twice.** Two entries naming one group is one parent asked
 * twice, and a set with a repeat in it is not a set. [of] refuses rather than
 * folding them, because the two entries carry two certificates and picking one
 * would be this device deciding which of the parent's statements counts.
 *
 * **The order is canonical.** Sorted by parent id, so two devices assembling the
 * same lineage from the same certificates emit the same bytes. That matters for
 * one reason only, and it is a reason about other people's builds: a reader that
 * predates several parents takes the *first* `parent_group` and the *first*
 * `birth_certificate` off the tags and checks them against each other. Emitted
 * in sorted pairs, such a reader files a true, partial lineage -- one parent,
 * verified. Emitted in two unordered runs it would pair one parent's id with
 * another's certificate, fail the check, and drop the whole state, losing the row
 * that says which ceremony's share signs for the room. See
 * `GroupKeyStateEvent.assembleTags`.
 *
 * ### Nothing here is evidence
 *
 * Holding one of these says only that somebody assembled it. Every certificate
 * in it is checked by `SubgroupBirthCertificateEvent.certifies` on every read --
 * see `GroupKeyStateManager.stateFrom`, which drops the whole state when any one
 * of them fails. This type holds the shape; the signatures hold the trust.
 */
class SubgroupLineage private constructor(
    /** The parents, in canonical order. Never empty, never repeating. */
    val parents: List<SubgroupParentage>,
) {
    /** The parents' room ids, in the same canonical order. */
    val parentChatRoomIds: List<HexKey> get() = parents.map { it.parentChatRoomId }

    /** How many groups claim this room as a child. At least one. */
    val size: Int get() = parents.size

    /** Whether [parentChatRoomId] is one of them. Hex, so case does not count. */
    fun names(parentChatRoomId: HexKey): Boolean =
        parents.any { it.parentChatRoomId.equals(parentChatRoomId, ignoreCase = true) }

    /**
     * This lineage with [parent] added, or null if it already names that group.
     *
     * An adoption, as the child's quorum will sign it. It takes the whole
     * resulting set rather than the one addition because a key state is
     * replaceable and the newest wins *wholesale* -- so a re-announcement that
     * carried only the new parent would silently drop the old one. Expressing the
     * addition as an addition is what stops a caller assembling that by hand and
     * forgetting.
     */
    fun plus(parent: SubgroupParentage): SubgroupLineage? =
        if (names(parent.parentChatRoomId)) null else of(parents + parent)

    /**
     * This lineage without [parentChatRoomId], or null when it was the last one.
     *
     * Null is "no lineage" rather than a failure, and is what a caller passes on
     * to say a room is nobody's child. Dropping a parent that is not named
     * returns this lineage unchanged, since the result is the same either way.
     *
     * It does not revoke anything. The certificate is a signature and exists
     * forever; what this removes is the child's half of the link, which is the
     * half that decides. The parent's own list is built from its certificates and
     * will go on naming the child -- see `SubgroupManager.subgroupsOf`.
     */
    fun minus(parentChatRoomId: HexKey): SubgroupLineage? = of(
        parents.filterNot { it.parentChatRoomId.equals(parentChatRoomId, ignoreCase = true) }
    )

    companion object {
        /**
         * A lineage of [parents], or null if there are none or one repeats.
         *
         * Null for empty is deliberate and is the same null every parentage API
         * here uses for "nobody's child": a room with no parents has no lineage
         * rather than an empty one, so there is one way to say it instead of two.
         */
        fun of(parents: List<SubgroupParentage>): SubgroupLineage? {
            if (parents.isEmpty()) return null

            val distinct = parents.distinctBy { it.parentChatRoomId.lowercase() }
            if (distinct.size != parents.size) return null

            return SubgroupLineage(distinct.sortedBy { it.parentChatRoomId.lowercase() })
        }

        /**
         * A lineage of one, which cannot fail -- a single entry has nothing to
         * repeat. The founding case, and the shape every state signed before
         * several parents existed is read back as.
         */
        fun of(parent: SubgroupParentage): SubgroupLineage = SubgroupLineage(listOf(parent))
    }
}
