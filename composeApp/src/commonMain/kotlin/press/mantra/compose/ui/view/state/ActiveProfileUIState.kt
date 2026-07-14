package press.mantra.compose.ui.view.state

sealed interface ActiveProfileUIState {
    data class Loaded(
        val localNostrEvent: press.mantra.compose.database.model.intermdiate.LocalNostrEvent
    ): ActiveProfileUIState

    data object Error: ActiveProfileUIState
    data object NotFound: ActiveProfileUIState
    data object Loading: ActiveProfileUIState
}

