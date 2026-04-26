package ac.cord.auxiliary.compose.ui.composable.navigation.routes

import kotlinx.serialization.Serializable

@Serializable
data class WriteNewNoteRoute(
    val inReplyToEventId: String? = null,
    val quotedEventId: String? = null,
): Route() {
}