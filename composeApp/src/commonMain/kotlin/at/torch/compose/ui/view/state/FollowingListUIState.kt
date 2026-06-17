package at.torch.compose.ui.view.state

sealed interface FollowingListUIState {
    data class Loaded(
        val following: List<at.torch.compose.database.model.Profile>
    ): FollowingListUIState

    data object Error: FollowingListUIState
    data object Loading: FollowingListUIState
}

