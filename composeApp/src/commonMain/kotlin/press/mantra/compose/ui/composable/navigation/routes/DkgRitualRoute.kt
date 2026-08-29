package press.mantra.compose.ui.composable.navigation.routes

import kotlinx.serialization.Serializable

/** The shared-key ceremony for one NIP-17 group. */
@Serializable
data class DkgRitualRoute(
    val activeUserPublicKey: String,
    val chatRoomId: String
): Route()
