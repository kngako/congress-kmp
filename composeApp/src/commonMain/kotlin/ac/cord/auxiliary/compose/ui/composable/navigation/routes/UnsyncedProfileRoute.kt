package ac.cord.auxiliary.compose.ui.composable.navigation.routes

import kotlinx.serialization.Serializable

@Serializable
data class UnsyncedProfileRoute(
    val publicKey: String,
): Route() {
}