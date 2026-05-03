package ac.cord.auxiliary.compose.ui.composable.widgets.content

import ac.cord.auxiliary.compose.database.model.intermdiate.LocalQuotedNostrEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp


@Composable
internal fun QuotedAddressableNote(
    localQuotedNostrEvent: LocalQuotedNostrEvent?,
    kind: Int,
    dTag: String,
    author: String,
    relayHints: List<String>,
    onNoteClick: (String) -> Unit,
    onProfileClick: ((String) -> Unit)?,
    noteActions: NoteActions?,
    style: TextStyle
) {

    // TODO: Sync quoted event...

    if (localQuotedNostrEvent != null) {
        QuotedNote(
            localQuotedNostrEvent = localQuotedNostrEvent,
            onNavigateToEvent = onNoteClick,
            noteActions = noteActions
        )
    } else {
        // Loading state
        Surface(
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(14.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .width(14.dp)
                        .height(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Loading note...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
