package press.mantra.compose.ui.composable.navigation.routes

import kotlinx.serialization.Serializable

/**
 * A dialect, and what is written in it.
 *
 * [chatRoomId] rides along although the dialect row already names its room,
 * because every sibling detail route carries it and the screens pass it on to
 * the routes they open. Reading it off the row instead would make this the one
 * route that cannot be navigated to before its entity has loaded.
 */
@Serializable
data class DialectDetailRoute(
    val activeUserPublicKey: String,
    val dialectId: String,
    val chatRoomId: String,
    val relayHint: String? = null
): Route()
