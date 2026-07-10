package at.torch.compose.ui.composable.navigation.routes

import kotlinx.serialization.Serializable

@Serializable
data class AddMemberToChatRoomConfirmationRoute(
    val activeUserPublicKey: String,
    val chatRoomId: String, // TODO: have this as a publicKey
    val relayHint: String?
): Route()