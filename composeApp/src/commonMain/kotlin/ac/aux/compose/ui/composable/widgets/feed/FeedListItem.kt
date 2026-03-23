package ac.aux.compose.ui.composable.widgets.feed

import androidx.compose.material3.ElevatedCard
import androidx.compose.runtime.Composable
import com.vitorpamplona.quartz.nip01Core.core.Event

@Composable
fun FeedListItem(
    event: Event,
    onNavigateToEvent: (Event) -> Unit
) {
    ElevatedCard(
        onClick = {
            onNavigateToEvent.invoke(event)
        }
    ) {
        event.ListView()
    }
}