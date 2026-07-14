package press.mantra.compose.ui.composable.navigation.routes

import kotlinx.serialization.Serializable

@Serializable
data class CreateProfileRoute(
    val nostrEventId: String? = null,
): Route()