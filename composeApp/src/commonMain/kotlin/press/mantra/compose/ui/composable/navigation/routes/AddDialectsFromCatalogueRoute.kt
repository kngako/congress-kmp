package press.mantra.compose.ui.composable.navigation.routes

import kotlinx.serialization.Serializable

/**
 * Picking dialects out of the bundled catalogue instead of typing one in.
 *
 * The same three arguments as [AddDialectRoute], and a separate destination
 * rather than a mode on that one: the form takes three text fields and makes one
 * dialect, and this takes a selection and makes a wave of them. One screen doing
 * both would have a `Loaded` state carrying the fields *and* the selection, and a
 * button meaning two different things.
 */
@Serializable
data class AddDialectsFromCatalogueRoute(
    val activeUserPublicKey: String,
    val chatRoomId: String,
    val relayHint: String?
): Route()
