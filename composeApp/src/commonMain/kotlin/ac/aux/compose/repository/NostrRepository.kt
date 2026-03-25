package ac.aux.compose.repository

import ac.aux.compose.database.model.NostrEvent
import ac.aux.compose.database.model.Profile
import ac.aux.compose.database.model.UnsignedNostrEvent
import ac.aux.compose.ui.view.state.CreateProfileUIState

interface NostrRepository {
    suspend fun createNewProfile(
        name: String,
        biography: String,
        onError: () -> Unit,
        onUnsignProfile: (UnsignedNostrEvent) -> Unit,
        onUnbroadcastedProfile: (NostrEvent) -> Unit,
        onProfileReady: (Profile) -> Unit
    )

    suspend fun saveUnsignedNostrEvent(unsignedNostrEvent: UnsignedNostrEvent): UnsignedNostrEvent?
}