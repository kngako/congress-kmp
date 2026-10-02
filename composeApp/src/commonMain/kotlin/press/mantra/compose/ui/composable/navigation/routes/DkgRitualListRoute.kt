package press.mantra.compose.ui.composable.navigation.routes

import kotlinx.serialization.Serializable

/**
 * Every key ceremony a room holds.
 *
 * The room's list, not the reader's: a ceremony is run by the whole group, and
 * which of them are waiting on this member is a status on each row rather than
 * a different list -- the same shape as [ProposalListRoute], for the same
 * reason.
 *
 * A room holds more than one as a matter of course since a subgroup's ChillDKG
 * moved into its parent's room, and until this there was no screen that could
 * name the second: [DkgRitualRoute] without a session id shows the newest, and
 * the newest is routinely the group's own, long finished, with the subgroup's
 * request buried underneath it.
 */
@Serializable
data class DkgRitualListRoute(
    val activeUserPublicKey: String,
    val chatRoomId: String
): Route()
