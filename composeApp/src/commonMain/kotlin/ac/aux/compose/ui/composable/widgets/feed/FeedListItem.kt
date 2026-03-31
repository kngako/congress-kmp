package ac.aux.compose.ui.composable.widgets.feed

import ac.aux.compose.database.model.NostrEvent
import ac.aux.compose.database.model.intermdiate.LocalNostrEvent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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