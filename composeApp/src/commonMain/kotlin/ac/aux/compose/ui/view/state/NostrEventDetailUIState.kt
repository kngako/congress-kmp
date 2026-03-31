package ac.aux.compose.ui.view.state

import ac.aux.compose.database.model.NostrEvent
import ac.aux.compose.database.model.intermdiate.LocalNostrEvent

sealed interface NostrEventDetailUIState {
    data class Loaded(
        val localNostrEvent: LocalNostrEvent
    ): NostrEventDetailUIState

    data object Error: NostrEventDetailUIState
    data object NotFound: NostrEventDetailUIState
    data object Loading: NostrEventDetailUIState
}

