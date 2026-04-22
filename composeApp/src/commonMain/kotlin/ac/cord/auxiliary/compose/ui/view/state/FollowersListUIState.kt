package ac.cord.auxiliary.compose.ui.view.state

import ac.cord.auxiliary.compose.database.model.Profile

sealed interface FollowersListUIState {
    data class Loaded(
        val followers: List<Profile>
    ): FollowersListUIState

    data object Error: FollowersListUIState
    data object Loading: FollowersListUIState
}

