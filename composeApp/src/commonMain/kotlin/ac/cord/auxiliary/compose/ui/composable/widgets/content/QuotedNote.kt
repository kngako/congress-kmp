package ac.cord.auxiliary.compose.ui.composable.widgets.content

import ac.cord.auxiliary.compose.database.model.NostrEvent
import ac.cord.auxiliary.compose.database.model.Profile
import ac.cord.auxiliary.compose.database.model.QuotedRelation
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp

@Composable
fun QuotedNote(
    quotedNostrEvent: QuotedRelation? = null,
    quotedProfile: Profile? = null,
    relayHints: List<String> = emptyList(),
    onNoteClick: ((String) -> Unit)? = null,
    noteActions: NoteActions? = null
) {
    // Observe versions so we recompose when data arrives from relays

    // TODO: Trigger on-demand fetch if the quoted event isn't cached
//    LaunchedEffect(eventId) {
//        if (eventRepo.getEvent(eventId) == null) {
//            eventRepo.requestQuotedEvent(eventId, relayHints)
//        }
//    }

    val effectiveNoteClick = noteActions?.onNoteClick ?: onNoteClick

    // Fetch poll votes when quoted event is a poll
//    LaunchedEffect(event?.id, event?.kind) {
//        if (event != null && event.kind == com.wisp.app.nostr.Nip88.KIND_POLL) {
//            eventRepo.requestPollVotes(event.id)
//        }
//        if (event != null && event.kind == com.wisp.app.nostr.Nip69.KIND_ZAP_POLL) {
//            eventRepo.requestZapPollVotes(event.id)
//        }
//    }

    if (quotedNostrEvent != null && noteActions != null) {
        // Full rendering with all interactive features

        Surface(
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            Text("Quote render coming soon")
//            quotedNostrEvent.RenderNotePreview()
        }
    } else if (quotedNostrEvent != null) {
        // Simple fallback rendering (no noteActions available)
        Surface(
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .then(
                    if (effectiveNoteClick != null) Modifier.clickable { effectiveNoteClick(quotedNostrEvent.quotedNostrEventId) }
                    else Modifier
                )
        ) {
            Text("Quote render coming soon")
//            quotedPost.RenderNotePreview()
        }
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