package ac.cord.auxiliary.compose.ui.view.state

import ac.cord.auxiliary.compose.database.model.Profile

sealed interface FollowingListUIState {
    data class Loaded(
        val profilesBeingFollowed: List<Profile>
    ): FollowingListUIState

    data object Error: FollowingListUIState
    data object Loading: FollowingListUIState
}

