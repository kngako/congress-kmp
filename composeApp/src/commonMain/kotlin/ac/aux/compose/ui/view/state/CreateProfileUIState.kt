package ac.aux.compose.ui.view.state

import ac.aux.compose.database.model.NostrEvent
import ac.aux.compose.database.model.Profile
import ac.aux.compose.database.model.UnsignedNostrEvent

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