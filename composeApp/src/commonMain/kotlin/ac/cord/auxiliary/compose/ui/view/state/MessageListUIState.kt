package ac.cord.auxiliary.compose.ui.view.state

import ac.cord.auxiliary.compose.database.model.Profile

sealed interface MessageListUIState {
    data class Loaded(
        val messageList: List<Profile>
    ): MessageListUIState

    data object Error: MessageListUIState
    data object Loading: MessageListUIState
}

