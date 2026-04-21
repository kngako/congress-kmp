package ac.cord.auxiliary.compose.ui.view.state

import ac.cord.auxiliary.compose.database.model.Profile

abstract class CreateProfileUIState {
    data object Error: CreateProfileUIState()

    data object Declaration: CreateProfileUIState()

    data object InputPrompt: CreateProfileUIState()

    data class ConfirmInput(
        val name: String,
        val bio: String
    ) : CreateProfileUIState()

    data class ProfileReady(
        val profile: Profile
    ) : CreateProfileUIState()
}