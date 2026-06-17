package at.torch.compose.ui.view.state


sealed interface HomeScreenUIState {
    data class Loaded(
        val profileWithFollowing: at.torch.compose.database.model.intermdiate.LocalProfileWithFollowing
    ): HomeScreenUIState

    data object Error: HomeScreenUIState
    data object Loading: HomeScreenUIState
}

