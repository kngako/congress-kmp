package ac.cord.auxiliary.compose.ui.view.state

import ac.cord.auxiliary.compose.database.model.intermdiate.LocalProfile
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalProfileWithFollowing

abstract class WriteNewNoteUIState {
    data object Loading: WriteNewNoteUIState()

    data object Error: WriteNewNoteUIState()

    data class InputPrompt(
        val localProfile: LocalProfileWithFollowing
    ): WriteNewNoteUIState()

    data class ConfirmNote(
        val localProfile: LocalProfileWithFollowing,
        val text: String,
    ) : WriteNewNoteUIState()

}