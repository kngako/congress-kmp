package press.mantra.compose.ui.view.state


sealed interface HomeScreenUIState {
    data class Loaded(
        val profileWithFollowing: press.mantra.compose.database.model.intermdiate.LocalProfileWithFollowing
    ): HomeScreenUIState

    data object Error: HomeScreenUIState
    data object Loading: HomeScreenUIState
}

