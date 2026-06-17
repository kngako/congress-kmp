package at.torch.compose.ui.view.state


sealed interface MetadataEventDetailUIState {
    data class Loaded(
        val isActiveUser: Boolean,
        val followingActiveUserConnection: at.torch.compose.database.model.Connection?,
        val followedByActiveUserConnection: at.torch.compose.database.model.Connection?,
    ): MetadataEventDetailUIState

    data object Error: MetadataEventDetailUIState
    data object Loading: MetadataEventDetailUIState
}

