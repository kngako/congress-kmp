package ac.aux.compose.ui.composable.navigation.routes

import ac.aux.compose.database.model.NostrEvent
import ac.aux.compose.database.model.Profile
import kotlinx.serialization.Serializable
import kotlin.time.Clock

@Serializable
data class UnindexedProfileRoute(
    val nostrEventId: String,
): Route() {

}