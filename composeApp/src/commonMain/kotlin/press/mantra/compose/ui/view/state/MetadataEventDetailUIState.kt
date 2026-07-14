package press.mantra.compose.ui.view.state


sealed interface MetadataEventDetailUIState {
    data class Loaded(
        val isActiveUser: Boolean,
        val followingActiveUserConnection: press.mantra.compose.database.model.Connection?,
        val followedByActiveUserConnection: press.mantra.compose.database.model.Connection?,
    ): MetadataEventDetailUIState

    data object Error: MetadataEventDetailUIState
    data object Loading: MetadataEventDetailUIState
}

