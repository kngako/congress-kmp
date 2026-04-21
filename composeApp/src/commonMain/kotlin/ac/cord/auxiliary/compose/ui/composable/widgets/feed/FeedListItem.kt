package ac.cord.auxiliary.compose.ui.composable.widgets.feed

import ac.cord.auxiliary.compose.database.model.intermdiate.LocalNostrEvent
import androidx.compose.runtime.Composable
import com.vitorpamplona.quartz.nip01Core.core.HexKey

@Composable
fun FeedListItem(
    localNostrEvent: LocalNostrEvent,
    onNavigateToEvent: (HexKey) -> Unit
) {
    localNostrEvent.ListView(
        onNavigateToEvent = onNavigateToEvent
    )
}