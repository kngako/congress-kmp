package press.mantra.compose.ui.composable.navigation.routes

import kotlinx.serialization.Serializable

/**
 * Which group to ask to adopt this one.
 *
 * The first of an adoption's two signings, and the only one that needs a screen
 * of its own: the acceptance is a card on the child's own detail screen, because
 * by then there is something to accept.
 *
 * Carries only the child's id. The candidates are not on the route and must not
 * be: they are the rooms the **profile in the chair** can act in, which a route
 * cannot know and which changes while the screen is open -- see
 * `SubgroupManager.adoptingCandidatesFor`.
 */
@Serializable
data class SelectAdoptingGroupRoute(
    val activeUserPublicKey: String,
    val chatRoomId: String,
    val relayHint: String? = null
): Route()
