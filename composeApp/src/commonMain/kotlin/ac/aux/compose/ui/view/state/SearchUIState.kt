package ac.aux.compose.ui.view.state

import ac.aux.compose.database.model.intermdiate.LocalNostrEvent

sealed interface SearchUIState {
    data class Loaded(
        val localNostrEvents: List<LocalNostrEvent>
    ): SearchUIState

    data object Error: SearchUIState
    data object Prompt: SearchUIState

    data object LandingSearch: SearchUIState
}

