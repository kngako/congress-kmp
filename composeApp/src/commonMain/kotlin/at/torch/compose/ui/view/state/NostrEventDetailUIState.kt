package at.torch.compose.ui.view.state

sealed interface NostrEventDetailUIState {
    data class Loaded(
        val localNostrEvent: at.torch.compose.database.model.intermdiate.LocalNostrEvent
    ): NostrEventDetailUIState

    data object Error: NostrEventDetailUIState
    data object NotFound: NostrEventDetailUIState
    data object Loading: NostrEventDetailUIState
}

