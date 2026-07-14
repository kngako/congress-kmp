package press.mantra.compose.ui.view.state

sealed interface FollowersListUIState {
    data class Loaded(
        val followers: List<press.mantra.compose.database.model.Profile>
    ): FollowersListUIState

    data object Error: FollowersListUIState
    data object Loading: FollowersListUIState
}

