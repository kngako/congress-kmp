package press.mantra.compose.ui.view.state

sealed interface FeedListUIState {
    data class Loaded(
        val localNostrEvents: List<press.mantra.compose.database.model.intermdiate.LocalNostrEvent>
    ): FeedListUIState

    data object Error: FeedListUIState
    data object Loading: FeedListUIState
}

