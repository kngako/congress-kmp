package ac.aux.compose.ui.composable.navigation.routes

import kotlinx.serialization.Serializable

@Serializable
data class SearchResultRoute(
    val query: String
): Route()