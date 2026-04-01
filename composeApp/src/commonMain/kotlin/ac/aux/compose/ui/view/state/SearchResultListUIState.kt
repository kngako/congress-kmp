package ac.aux.compose.ui.view.state

import ac.aux.compose.database.model.intermdiate.LocalNostrEvent

sealed interface SearchResultListUIState {
    data class Loaded(
        val localNostrEvents: List<LocalNostrEvent>
    ): SearchResultListUIState

    data object Error: SearchResultListUIState
    data object Loading: SearchResultListUIState
}

