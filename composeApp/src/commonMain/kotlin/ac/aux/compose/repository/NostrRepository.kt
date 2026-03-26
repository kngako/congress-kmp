package ac.aux.compose.repository

import ac.aux.compose.database.model.NostrEvent
import ac.aux.compose.database.model.Profile
import ac.aux.compose.database.model.UnsignedNostrEvent
import ac.aux.compose.database.model.intermdiate.LocalProfile
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.flow.Flow

interface NostrRepository {
    suspend fun observeProfile(publicKey: HexKey): Flow<LocalProfile?>

    suspend fun observeUnsignedNostrEvents(publicKey: HexKey): Flow<UnsignedNostrEvent>

    suspend fun createNewProfile(
        publicKey: HexKey,
        name: String,
        biography: String,
        onError: () -> Unit,
//        onUnsignProfile: (UnsignedNostrEvent) -> Unit,
//        onUnbroadcastedProfile: (NostrEvent) -> Unit,
        onProfileReady: (Profile) -> Unit
    )

    suspend fun saveUnsignedNostrEvent(unsignedNostrEvent: UnsignedNostrEvent): UnsignedNostrEvent?

    suspend fun wipeDatabase()

    suspend fun publishNostrEvent(
        nostrEvent: NostrEvent,
        relayURLs: List<String> = emptyList()
    )

    companion object {
        val NO_OP_NOSTR_REPOSITORY = object : NostrRepository {
            override suspend fun observeProfile(publicKey: HexKey): Flow<LocalProfile?> {
                TODO("Not yet implemented")
            }

            override suspend fun observeUnsignedNostrEvents(publicKey: HexKey): Flow<UnsignedNostrEvent> {
                TODO("Not yet implemented")
            }

            override suspend fun createNewProfile(
                publicKey: HexKey,
                name: String,
                biography: String,
                onError: () -> Unit,
                onProfileReady: (Profile) -> Unit
            ) {
            }

            override suspend fun saveUnsignedNostrEvent(unsignedNostrEvent: UnsignedNostrEvent): UnsignedNostrEvent? {
                return null
            }

            override suspend fun wipeDatabase() {
            }

            override suspend fun publishNostrEvent(nostrEvent: NostrEvent, relayURLs: List<String>) {

            }
        }
    }
}