package ac.aux.compose.ui.view.state

import ac.aux.compose.database.model.BroadcastNostrEventReceipt
import ac.aux.compose.database.model.BroadcastNostrEventRequest
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

    data class UnsyncedProfile(
        val unsignedNostrEvent: UnsignedNostrEvent,
    ) : NavigationUIState()

    data class UnqueuedProfileSynchronization(
        val unsignedNostrEvent: UnsignedNostrEvent,
    ) : NavigationUIState()

    data class UnindexedProfile(
        val nostrEvent: NostrEvent,
    ) : NavigationUIState()

    data class UnqueuedProfile(
        val profile: Profile,
    ) : NavigationUIState()


    data class UnannouncedProfile(
        val broadcastNostrEventRequest: BroadcastNostrEventRequest,
    ) : NavigationUIState()



    data class ProfileLoaded(
        val publicKey: String
    ) : NavigationUIState()
}