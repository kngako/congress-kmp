package ac.aux.compose.ui.composable.navigation.routes

import ac.aux.compose.database.model.NostrEvent
import ac.aux.compose.database.model.Profile
import kotlinx.serialization.Serializable
import kotlin.time.Clock

@Serializable
data class CreateProfileRoute(
    val nostrEventId: String? = null,
): Route() {

    fun toPlaceholderNostrEvent(): NostrEvent? = nostrEventId?.let {
        NostrEvent(
            id = nostrEventId,
            pubKey = "",
            createdAt = Clock.System.now(),
            tags = emptyArray(),
            kind = 0,
            sig = "",
            content = ""
        )
    }
}