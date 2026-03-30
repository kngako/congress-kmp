package ac.aux.compose.ui.composable.widgets.feed

import ac.aux.compose.database.model.NostrEvent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.quartz.nip01Core.core.Event

@Composable
fun FeedListItem(
    nostrEvent: NostrEvent,
    onNavigateToEvent: (NostrEvent) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.padding(
            horizontal = 10.dp
        ),
        onClick = {
            onNavigateToEvent.invoke(nostrEvent)
        }
    ) {
        nostrEvent.ListView()
    }
}