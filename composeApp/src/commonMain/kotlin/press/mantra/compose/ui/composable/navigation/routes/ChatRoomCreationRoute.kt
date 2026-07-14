package press.mantra.compose.ui.composable.navigation.routes

import kotlinx.serialization.Serializable

@Serializable
data class ChatRoomCreationRoute(
    val activeUserPublicKey: String,
): Route()