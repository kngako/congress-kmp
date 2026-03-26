package ac.aux.compose.ui.composable.navigation.routes

import ac.aux.compose.database.model.UnsignedNostrEvent
import kotlinx.serialization.Serializable
import kotlin.time.Clock

@Serializable
data class UnsignedProfileRoute(
    val unsignedNostrEventId: Long,
): Route() {

    fun toPlaceholderUnsignedNostrEvent(): UnsignedNostrEvent = UnsignedNostrEvent(
        id = unsignedNostrEventId,
        pubKey = "",
        createdAt = Clock.System.now(),
        tags = emptyArray(),
        kind = 0,
        content = ""
    )
}