package ac.aux.compose.repository

import ac.aux.compose.database.model.BroadcastNostrEventReceipt
import ac.aux.compose.database.model.BroadcastNostrEventRequest
import ac.aux.compose.database.model.NostrEvent
import ac.aux.compose.database.model.Profile
import ac.aux.compose.database.model.RecentSearch
import ac.aux.compose.database.model.SynchronizeNostrEventRequest
import ac.aux.compose.database.model.UnsignedNostrEvent
import ac.aux.compose.database.model.intermdiate.LocalBroadcastNostrEventRequest
import ac.aux.compose.database.model.intermdiate.LocalNostrEvent
import ac.aux.compose.database.model.intermdiate.LocalAccount
import ac.aux.compose.database.model.intermdiate.LocalProfile
import ac.aux.compose.database.model.typealiases.SynchronizationFilterArray
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.flow.Flow

interface NostrRepository {
    suspend fun observeProfile(publicKey: HexKey): Flow<LocalAccount?>

    suspend fun observeUnsignedNostrEvents(publicKey: HexKey): Flow<UnsignedNostrEvent?>

    suspend fun observePendingBroadcastNostrEventRequests(): Flow<List<LocalBroadcastNostrEventRequest>>

    suspend fun observePendingSynchronizeNostrEventRequests(): Flow<SynchronizeNostrEventRequest?>

    suspend fun createNewProfile(
        publicKey: HexKey,
        name: String?,
        biography: String?,
        onError: () -> Unit,
//        onUnsignProfile: (UnsignedNostrEvent) -> Unit,
//        onUnbroadcastedProfile: (NostrEvent) -> Unit,
        onProfileReady: (Profile) -> Unit,
        synchronizationRelayURLs: List<String>
    )

    suspend fun createNewTextNote(
        publicKey: HexKey,
        text: String,
        onError: () -> Unit,
        onSuccess: () -> Unit
    )

    suspend fun saveUnsignedNostrEvent(unsignedNostrEvent: UnsignedNostrEvent): UnsignedNostrEvent?

    suspend fun wipeDatabase()

    suspend fun publishNostrEvent(
        unsignedNostrEvent: UnsignedNostrEvent,
        nostrEvent: NostrEvent,
        relayURLs: List<String> = emptyList()
    )

    suspend fun broadcastProcessed(broadcastNostrEventRequest: BroadcastNostrEventRequest, status: String = "processing")

    suspend fun synchronizeNostrEventRequestProcessed(synchronizeNostrEventRequest: SynchronizeNostrEventRequest)

    suspend fun saveBroadcastReceipt(broadcastNostrEventReceipt: BroadcastNostrEventReceipt)

    suspend fun saveNostrEvent(
        nostrEvent: NostrEvent,
        synchronizeNostrEventRequest: SynchronizeNostrEventRequest,
        synchronizationRelayURLs: List<String>
    )

    suspend fun queueSynchronizeNostrEvent(
        synchronizeNostrEventRequests: List<SynchronizeNostrEventRequest>,
    )

    suspend fun observeSynchronizeNostrEventRequestByPurposeAndStatusCount(purpose: String): Flow<Int>

    suspend fun observeNostrFeed(): Flow<List<LocalNostrEvent>>

    suspend fun observeLocalNostrEventById(nostrEventId: String): Flow<LocalNostrEvent?>

    suspend fun searchableProfiles(): List<Profile>

    suspend fun filterProfiles(searchTerm: String): List<LocalProfile>

    suspend fun getRecentSearches(): List<RecentSearch>

    suspend fun removeRecentSearch(recentSearch: RecentSearch)

    suspend fun saveSearchQuery(query: String, synchronizationFilterArray: SynchronizationFilterArray? = null)

    companion object {
        val NO_OP_NOSTR_REPOSITORY = object : NostrRepository {
            override suspend fun observeProfile(publicKey: HexKey): Flow<LocalAccount?> {
                TODO("Not yet implemented")
            }

            override suspend fun observeUnsignedNostrEvents(publicKey: HexKey): Flow<UnsignedNostrEvent?> {
                TODO("Not yet implemented")
            }

            override suspend fun observePendingBroadcastNostrEventRequests(): Flow<List<LocalBroadcastNostrEventRequest>> {
                TODO("Not yet implemented")
            }

            override suspend fun observePendingSynchronizeNostrEventRequests(): Flow<SynchronizeNostrEventRequest?> {
                TODO("Not yet implemented")
            }

            override suspend fun createNewProfile(
                publicKey: HexKey,
                name: String?,
                biography: String?,
                onError: () -> Unit,
                onProfileReady: (Profile) -> Unit,
                synchronizationRelayURLs: List<String>
            ) {
            }

            override suspend fun createNewTextNote(
                publicKey: HexKey,
                text: String,
                onError: () -> Unit,
                onSuccess: () -> Unit
            ) {
                TODO("Not yet implemented")
            }

            override suspend fun saveUnsignedNostrEvent(unsignedNostrEvent: UnsignedNostrEvent): UnsignedNostrEvent? {
                return null
            }

            override suspend fun wipeDatabase() {
            }

            override suspend fun publishNostrEvent(
                unsignedNostrEvent: UnsignedNostrEvent,
                nostrEvent: NostrEvent,
                relayURLs: List<String>
            ) {

            }

            override suspend fun broadcastProcessed(
                broadcastNostrEventRequest: BroadcastNostrEventRequest,
                status: String
            ) {
                TODO("Not yet implemented")
            }

            override suspend fun synchronizeNostrEventRequestProcessed(synchronizeNostrEventRequest: SynchronizeNostrEventRequest) {
                TODO("Not yet implemented")
            }

            override suspend fun saveBroadcastReceipt(broadcastNostrEventReceipt: BroadcastNostrEventReceipt) {
                TODO("Not yet implemented")
            }

            override suspend fun saveNostrEvent(
                nostrEvent: NostrEvent,
                synchronizeNostrEventRequest: SynchronizeNostrEventRequest,
                synchronizationRelayURLs: List<String>
            ) {
                TODO("Not yet implemented")
            }

            override suspend fun queueSynchronizeNostrEvent(synchronizeNostrEventRequests: List<SynchronizeNostrEventRequest>) {
                TODO("Not yet implemented")
            }

            override suspend fun observeSynchronizeNostrEventRequestByPurposeAndStatusCount(purpose: String): Flow<Int> {
                TODO("Not yet implemented")
            }

            override suspend fun observeNostrFeed(): Flow<List<LocalNostrEvent>> {
                TODO("Not yet implemented")
            }

            override suspend fun observeLocalNostrEventById(nostrEventId: String): Flow<LocalNostrEvent?> {
                TODO("Not yet implemented")
            }

            override suspend fun searchableProfiles(): List<Profile> {
                TODO("Not yet implemented")
            }

            override suspend fun filterProfiles(searchTerm: String): List<LocalProfile> {
                TODO("Not yet implemented")
            }

            override suspend fun getRecentSearches(): List<RecentSearch> {
                TODO("Not yet implemented")
            }

            override suspend fun removeRecentSearch(recentSearch: RecentSearch) {
                TODO("Not yet implemented")
            }

            override suspend fun saveSearchQuery(
                query: String,
                synchronizationFilterArray: SynchronizationFilterArray?
            ) {
                TODO("Not yet implemented")
            }
        }
    }
}