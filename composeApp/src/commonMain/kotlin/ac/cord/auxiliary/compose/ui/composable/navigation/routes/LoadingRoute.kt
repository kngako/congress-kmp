package ac.cord.auxiliary.compose.ui.composable.navigation.routes

import kotlinx.serialization.Serializable

@Serializable
data class LoadingRoute(
    val text: String? = null
): Route()