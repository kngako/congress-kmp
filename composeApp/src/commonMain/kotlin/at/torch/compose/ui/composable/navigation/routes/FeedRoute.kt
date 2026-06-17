package at.torch.compose.ui.composable.navigation.routes

import kotlinx.serialization.Serializable

@Serializable
data class FeedRoute(
    val activeUserPublicKey: String
): Route()