package press.mantra.compose.ui.view.state

sealed interface FollowingListUIState {
    data class Loaded(
        val following: List<press.mantra.compose.database.model.Profile>
    ): FollowingListUIState

    data object Error: FollowingListUIState
    data object Loading: FollowingListUIState
}

