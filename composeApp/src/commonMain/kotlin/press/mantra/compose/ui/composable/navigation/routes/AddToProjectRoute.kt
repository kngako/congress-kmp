package press.mantra.compose.ui.composable.navigation.routes

import kotlinx.serialization.Serializable

/**
 * [translations] picks which half of a project this adds to: its translations
 * when true, its source versions when false.
 *
 * One route rather than two because the screen is the same screen -- a list to
 * pick from and one proposal -- and the two differ only in which list and which
 * kind. Two routes would be two view models with one body between them.
 */
@Serializable
data class AddToProjectRoute(
    val activeUserPublicKey: String,
    val projectId: String,
    val chatRoomId: String,
    val translations: Boolean,
    val relayHint: String?
): Route()
