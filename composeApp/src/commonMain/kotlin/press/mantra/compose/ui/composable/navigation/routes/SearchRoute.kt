package press.mantra.compose.ui.composable.navigation.routes

import kotlinx.serialization.Serializable

@Serializable
data class SearchRoute(
    val activeUserPublicKey: String,
): Route()