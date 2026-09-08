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
    val parentChatRoomId: String? = null
): Route()
