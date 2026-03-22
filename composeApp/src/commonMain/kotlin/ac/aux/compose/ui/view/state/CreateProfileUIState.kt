package ac.aux.compose.ui.view.state

abstract class CreateProfileUIState {

    data object Declaration: CreateProfileUIState()

    data object InputPrompt: CreateProfileUIState()

    data class ConfirmInput(
        val name: String,
        val bio: String
    ) : CreateProfileUIState()
}