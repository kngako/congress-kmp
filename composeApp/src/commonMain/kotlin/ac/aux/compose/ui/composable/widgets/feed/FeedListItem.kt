package ac.aux.compose.ui.composable.widgets.feed

import ac.aux.compose.database.model.NostrEvent
import ac.aux.compose.database.model.intermdiate.LocalNostrEvent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun FeedListItem(
    localNostrEvent: LocalNostrEvent,
    onNavigateToEvent: (NostrEvent) -> Unit
) {
    localNostrEvent.ListView(
        onNavigateToEvent = onNavigateToEvent
    )
}