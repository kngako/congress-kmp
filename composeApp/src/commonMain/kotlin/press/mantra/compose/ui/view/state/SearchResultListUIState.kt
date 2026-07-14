package press.mantra.compose.ui.view.state

sealed interface SearchResultListUIState {
    data class Loaded(
        val localNostrEvents: List<press.mantra.compose.database.model.intermdiate.LocalNostrEvent>
    ): SearchResultListUIState

    data object Error: SearchResultListUIState
    data object Loading: SearchResultListUIState
}

