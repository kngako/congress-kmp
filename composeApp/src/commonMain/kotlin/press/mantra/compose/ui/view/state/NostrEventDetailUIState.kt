package press.mantra.compose.ui.view.state

sealed interface NostrEventDetailUIState {
    data class Loaded(
        val localNostrEvent: press.mantra.compose.database.model.intermdiate.LocalNostrEvent
    ): NostrEventDetailUIState

    data object Error: NostrEventDetailUIState
    data object NotFound: NostrEventDetailUIState
    data object Loading: NostrEventDetailUIState
}

