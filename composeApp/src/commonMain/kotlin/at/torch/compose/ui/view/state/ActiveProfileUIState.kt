package at.torch.compose.ui.view.state

sealed interface ActiveProfileUIState {
    data class Loaded(
        val localNostrEvent: at.torch.compose.database.model.intermdiate.LocalNostrEvent
    ): ActiveProfileUIState

    data object Error: ActiveProfileUIState
    data object NotFound: ActiveProfileUIState
    data object Loading: ActiveProfileUIState
}

