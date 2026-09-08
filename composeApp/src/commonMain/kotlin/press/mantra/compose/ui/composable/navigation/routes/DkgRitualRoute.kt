package press.mantra.compose.ui.composable.navigation.routes

import kotlinx.serialization.Serializable

/**
 * The shared-key ceremony for one NIP-17 group.
 *
 * [parentChatRoomId] is set when the ceremony is making a subgroup, and turns the
 * screen from a three-step ladder into a four-step one -- the extra rung being the
 * parent's birth certificate. It is a claim rather than evidence, in the route as
 * on the wire: what makes a lineage real is the certificate the parent's quorum
 * signs, two steps later. See `docs/subgroups.md`.
 */
@Serializable
data class DkgRitualRoute(
    val activeUserPublicKey: String,
    val chatRoomId: String,
    val parentChatRoomId: String? = null,
    /**
     * Which of the room's ceremonies to show, when the caller knows.
     *
     * A room can hold more than one -- a subgroup whose admins are the whole
     * group runs in the room the group's own ceremony ran in -- so "the room's
     * ceremony" does not name one thing. A transcript line knows which it is
     * about and passes it; a caller with only a room passes null and gets the
     * newest, which is the best answer available to it.
     */
    val dkgSessionId: String? = null
): Route()
