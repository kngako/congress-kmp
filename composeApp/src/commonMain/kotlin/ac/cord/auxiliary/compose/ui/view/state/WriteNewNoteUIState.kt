package ac.cord.auxiliary.compose.ui.view.state

abstract class WriteNewNoteUIState {
    data object Error: WriteNewNoteUIState()

    data object InputPrompt: WriteNewNoteUIState()

    data class ConfirmNote(
        val text: String,
    ) : WriteNewNoteUIState()

}