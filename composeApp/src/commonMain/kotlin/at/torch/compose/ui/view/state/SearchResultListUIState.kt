package at.torch.compose.ui.view.state

sealed interface SearchResultListUIState {
    data class Loaded(
        val localNostrEvents: List<at.torch.compose.database.model.intermdiate.LocalNostrEvent>
    ): SearchResultListUIState

    data object Error: SearchResultListUIState
    data object Loading: SearchResultListUIState
}

