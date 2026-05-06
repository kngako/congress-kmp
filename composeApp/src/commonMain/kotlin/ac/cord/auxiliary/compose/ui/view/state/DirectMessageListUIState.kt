package ac.cord.auxiliary.compose.ui.view.state

import ac.cord.auxiliary.compose.database.model.Profile

sealed interface DirectMessageListUIState {
    data class Loaded(
        val directMessageList: List<Profile>
    ): DirectMessageListUIState

    data object Error: DirectMessageListUIState
    data object Loading: DirectMessageListUIState
}

