package press.mantra.compose.ui.view.state

import press.mantra.compose.database.model.intermdiate.LocalChatRoom

sealed interface AddArtifactUIState {
    data class Loaded(
        val localChatRoom: LocalChatRoom
    ): AddArtifactUIState

    data class Error(
        val message: String
    ): AddArtifactUIState

    data object Loading: AddArtifactUIState
}

