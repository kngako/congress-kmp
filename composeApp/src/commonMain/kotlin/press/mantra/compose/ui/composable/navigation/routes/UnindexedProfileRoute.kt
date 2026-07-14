package press.mantra.compose.ui.composable.navigation.routes

import kotlinx.serialization.Serializable

@Serializable
data class UnindexedProfileRoute(
    val nostrEventId: String,
): Route()