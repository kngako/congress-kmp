package press.mantra.compose.ui.view.state

sealed interface ShareProfileUIState {
    data class Loaded(
        val localNostrEvent: press.mantra.compose.database.model.intermdiate.LocalNostrEvent
    ): ShareProfileUIState

    data object Error: ShareProfileUIState
    data object NotFound: ShareProfileUIState
    data object Loading: ShareProfileUIState
}

