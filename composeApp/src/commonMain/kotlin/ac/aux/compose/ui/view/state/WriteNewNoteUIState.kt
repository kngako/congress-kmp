package ac.aux.compose.ui.view.state

import ac.aux.compose.database.model.NostrEvent
import ac.aux.compose.database.model.Profile
import ac.aux.compose.database.model.UnsignedNostrEvent

abstract class WriteNewNoteUIState {
    data object Error: WriteNewNoteUIState()

    data object InputPrompt: WriteNewNoteUIState()

    data class ConfirmNote(
        val text: String,
    ) : WriteNewNoteUIState()

}