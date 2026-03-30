package ac.aux.compose.ui.view.state

import ac.aux.compose.database.model.NostrEvent

sealed interface FeedListUIState {
    data class Loaded(
        val nostrEvents: List<NostrEvent>
    ): FeedListUIState

    data object Error: FeedListUIState
    data object Loading: FeedListUIState
}

