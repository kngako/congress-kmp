package ac.aux.compose.ui.composable.widgets.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.quartz.nip01Core.core.Event

@Composable
fun LoadedFeedView(
    events: List<Event>,
    onNavigateToEvent: (Event) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items(
            items = events,
            key = { e -> e.id }
        ) { event ->
            FeedListItem(
                event = event,
                onNavigateToEvent = {
                    onNavigateToEvent.invoke(event)
                }
            )
        }
    }
}