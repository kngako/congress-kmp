package ac.aux.compose.ui.composable.widgets.feed

import ac.aux.compose.database.model.NostrEvent
import androidx.compose.material3.ElevatedCard
import androidx.compose.runtime.Composable
import com.vitorpamplona.quartz.nip01Core.core.Event

@Composable
fun FeedListItem(
    nostrEvent: NostrEvent,
    onNavigateToEvent: (NostrEvent) -> Unit
) {
    ElevatedCard(
        onClick = {
            onNavigateToEvent.invoke(nostrEvent)
        }
    ) {
        nostrEvent.ListView()
    }
}