package ac.aux.compose.ui.view.state

import ac.aux.compose.database.model.NostrEvent
import ac.aux.compose.database.model.Profile
import ac.aux.compose.database.model.UnsignedNostrEvent

abstract class NavigationUIState {
    data object Error: NavigationUIState()

    data object Loading: NavigationUIState()

    data object Landing: NavigationUIState()

    data class UnsignedProfile(
        val unsignedNostrEvent: UnsignedNostrEvent,
    ) : NavigationUIState()

    data class UnannouncedProfile(
        val nostrEvent: NostrEvent,
    ) : NavigationUIState()

    data class ProfileLoaded(
        val profile: Profile
    ) : NavigationUIState()
}