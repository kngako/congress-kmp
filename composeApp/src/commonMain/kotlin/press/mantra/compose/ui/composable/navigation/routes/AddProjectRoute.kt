package press.mantra.compose.ui.composable.navigation.routes

import kotlinx.serialization.Serializable

@Serializable
data class AddProjectRoute(
    val chatRoomId: String,
    val activeUserPublicKey: String,
    val relayHint: String?
): Route()
