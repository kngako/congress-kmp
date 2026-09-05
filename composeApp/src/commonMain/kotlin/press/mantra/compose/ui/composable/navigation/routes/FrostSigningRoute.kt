package press.mantra.compose.ui.composable.navigation.routes

import kotlinx.serialization.Serializable

/**
 * One signing session.
 *
 * Carries a session id rather than only a room, because a group signs
 * repeatedly and can have more than one session open at a time -- unlike a
 * ceremony, where "the room's ritual" identifies it.
 */
@Serializable
data class FrostSigningRoute(
    val activeUserPublicKey: String,
    val chatRoomId: String,

    /**
     * Null when the caller does not know which session it means.
     *
     * A transcript line is the main case: chat rows carry no session, and adding
     * a column for one feature to a table every message uses is a poor trade for
     * a lookup the screen can do. It resolves to the room's live session, which
     * is the one a line is talking about in every case but a group running two
     * at once.
     */
    val sessionId: String? = null
): Route()
