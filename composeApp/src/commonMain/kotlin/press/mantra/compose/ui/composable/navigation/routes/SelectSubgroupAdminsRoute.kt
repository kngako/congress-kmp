package press.mantra.compose.ui.composable.navigation.routes

import kotlinx.serialization.Serializable

/**
 * First step of making a subgroup: who runs it, what it is called, and on what
 * quorum.
 *
 * All three have to be settled before anything is published. ChillDKG hashes the
 * threshold and the host keys into the session identity, so `t` is fixed the
 * moment the proposal goes out -- there is no later screen where it could be
 * changed, and a group that disagrees about it gets no key at all rather than a
 * weak one.
 */
@Serializable
data class SelectSubgroupAdminsRoute(
    val activeUserPublicKey: String,
    val parentChatRoomId: String,
    val relayHint: String? = null,

    /**
     * A name to open the field with, where the caller knows one.
     *
     * Set when the subgroup was asked for from a dialect, an artifact, a
     * translation or a project -- the entity's own name, bare, with no `#`; see
     * `SubgroupOrigin`. It is a suggestion and stays editable: a group naming its
     * own room something else is not this route's business to prevent.
     *
     * Null from the room's own "Add subgroup", which knows nothing to suggest.
     */
    val name: String? = null,

    /**
     * The entity the subgroup is for, flattened: a kind and an event id.
     *
     * Two nullable fields rather than one type, because a route carries no
     * domain types -- it is `@Serializable` and is reconstructed from a bundle.
     * They are reassembled into a `SubgroupOrigin.Ref` by the view model, which
     * requires both or neither: half a reference is a reference with the
     * resolvable part removed.
     */
    val originKind: Int? = null,
    val originId: String? = null,
): Route()
