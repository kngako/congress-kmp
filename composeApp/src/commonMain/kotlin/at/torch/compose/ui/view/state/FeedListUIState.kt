package at.torch.compose.ui.view.state

sealed interface FeedListUIState {
    data class Loaded(
        val localNostrEvents: List<at.torch.compose.database.model.intermdiate.LocalNostrEvent>
    ): FeedListUIState

    data object Error: FeedListUIState
    data object Loading: FeedListUIState
}

