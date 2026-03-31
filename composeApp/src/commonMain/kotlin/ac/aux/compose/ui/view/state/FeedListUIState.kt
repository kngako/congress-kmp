package ac.aux.compose.ui.view.state

import ac.aux.compose.database.model.NostrEvent
import ac.aux.compose.database.model.intermdiate.LocalNostrEvent

sealed interface FeedListUIState {
    data class Loaded(
        val localNostrEvents: List<LocalNostrEvent>
    ): FeedListUIState

    data object Error: FeedListUIState
    data object Loading: FeedListUIState
}

