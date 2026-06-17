package at.torch.compose.ui.view.state

sealed interface FollowersListUIState {
    data class Loaded(
        val followers: List<at.torch.compose.database.model.Profile>
    ): FollowersListUIState

    data object Error: FollowersListUIState
    data object Loading: FollowersListUIState
}

