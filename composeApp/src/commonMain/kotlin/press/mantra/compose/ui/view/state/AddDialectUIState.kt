package press.mantra.compose.ui.view.state

import press.mantra.compose.database.model.intermdiate.LocalChatRoom

sealed interface AddDialectUIState {
    data class Loaded(
        val localChatRoom: LocalChatRoom,
    ): AddDialectUIState

    data class Error(
        val message: String
    ): AddDialectUIState

    data object Loading: AddDialectUIState
}
