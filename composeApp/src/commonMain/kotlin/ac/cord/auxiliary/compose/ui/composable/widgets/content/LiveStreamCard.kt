package ac.cord.auxiliary.compose.ui.composable.widgets.content

import ac.cord.auxiliary.compose.database.model.intermdiate.LocalNostrEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
private fun LiveStreamCard(
    localNostrEvent: LocalNostrEvent,
    dTag: String,
    author: String,
    relayHints: List<String>,
    onProfileClick: ((String) -> Unit)?,
    onLiveStreamClick: ((String, String, String?) -> Unit)? = null,
    segmentRelayHints: List<String> = emptyList()
) {
    val event = localNostrEvent.nostrEvent
    val profile = localNostrEvent.profile

    // TODO: Request sync for this event
//    LaunchedEffect(author, dTag) {
//        if (eventRepo.findAddressableEvent(30311, author, dTag) == null) {
//            eventRepo.requestAddressableEvent(30311, author, dTag, relayHints)
//        }
//    }

    val title = remember(event) { event?.tags?.firstOrNull { it.size >= 2 && it[0] == "title" }?.get(1) }
    val summary = remember(event) { event?.tags?.firstOrNull { it.size >= 2 && it[0] == "summary" }?.get(1) }
    val image = remember(event) { event?.tags?.firstOrNull { it.size >= 2 && it[0] == "image" }?.get(1) }
    val status = remember(event) { event?.tags?.firstOrNull { it.size >= 2 && it[0] == "status" }?.get(1) }
    val streamUrl = remember(event) { event?.tags?.firstOrNull { it.size >= 2 && it[0] == "streaming" }?.get(1) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .then(
                if (onLiveStreamClick != null) Modifier.clickable {
                    onLiveStreamClick(author, dTag, segmentRelayHints.firstOrNull())
                }
                else Modifier
            )
    ) {
        LiveStreamCardContent(event, streamUrl, image, title, summary, status, profile, author, onProfileClick)
    }
}
