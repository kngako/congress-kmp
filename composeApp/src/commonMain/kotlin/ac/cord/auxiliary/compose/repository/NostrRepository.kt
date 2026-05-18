package ac.cord.auxiliary.compose.repository

import ac.cord.auxiliary.compose.database.model.BroadcastNostrEventReceipt
import ac.cord.auxiliary.compose.database.model.BroadcastNostrEventRequest
import ac.cord.auxiliary.compose.database.model.Connection
import ac.cord.auxiliary.compose.database.model.NegentropySynchronizeRequest
import ac.cord.auxiliary.compose.database.model.NostrEvent
import ac.cord.auxiliary.compose.database.model.Profile
import ac.cord.auxiliary.compose.database.model.RecentSearch
import ac.cord.auxiliary.compose.database.model.SynchronizeNostrEventRequest
import ac.cord.auxiliary.compose.database.model.UnsignedNostrEvent
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalBroadcastNostrEventRequest
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalNostrEvent
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalAccount
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalChatRoom
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalProfile
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalProfileWithFollowers
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalProfileWithFollowing
import ac.cord.auxiliary.compose.database.model.typealiases.SynchronizationFilterArray
import ac.cord.auxiliary.compose.database.model.types.SynchronizationFilter
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Kind
import kotlinx.coroutines.flow.Flow

interface NostrRepository {
    suspend fun observeProfile(publicKey: HexKey): Flow<LocalAccount?>

    suspend fun observeRelation(publicKey: HexKey, activePublicKey: HexKey): Flow<List<Connection>>

    suspend fun deleteConnection(connection: Connection)

    suspend fun observeUnsignedNostrEvents(publicKey: HexKey): Flow<UnsignedNostrEvent?>

    suspend fun observePendingBroadcastNostrEventRequests(): Flow<List<LocalBroadcastNostrEventRequest>>

    suspend fun observePendingSynchronizeNostrEventRequests(): Flow<SynchronizeNostrEventRequest?>

    suspend fun observePendingNegentropySynchronizeRequests(): Flow<NegentropySynchronizeRequest?>

    suspend fun createNewProfile(
        publicKey: HexKey,
        name: String?,
        biography: String?
    )

    suspend fun signInToProfile(
        publicKey: HexKey
    )

    suspend fun createNewTextNote(
        publicKey: HexKey,
        mentionedPublicKeys: List<HexKey>,
        textInput: String,
        inReplyToNostrEvent: LocalNostrEvent?,
        quotedNostrEvent: LocalNostrEvent?,
        onError: () -> Unit,
        onSuccess: () -> Unit
    )

    suspend fun saveUnsignedNostrEvent(unsignedNostrEvent: UnsignedNostrEvent): UnsignedNostrEvent?

    suspend fun saveUnsignedNostrEvents(unsignedNostrEvents: List<UnsignedNostrEvent>)

    suspend fun wipeDatabase()

    suspend fun publishNostrEvent(
        unsignedNostrEvent: UnsignedNostrEvent,
        nostrEvent: NostrEvent,
        relayURLs: List<String> = emptyList()
    )

    suspend fun broadcastProcessed(broadcastNostrEventRequest: BroadcastNostrEventRequest, status: String = "processing")

    suspend fun synchronizeNostrEventRequestProcessed(synchronizeNostrEventRequest: SynchronizeNostrEventRequest)

    suspend fun negentropySynchronizeRequestProcessed(negentropySynchronizeRequest: NegentropySynchronizeRequest)

    suspend fun saveBroadcastReceipt(broadcastNostrEventReceipt: BroadcastNostrEventReceipt)

    /**
     * Save nostrEvent from relays (and index for local viewing)...
     */
    suspend fun saveNostrEvent(
        nostrEvent: NostrEvent,
        synchronizeNostrEventRequest: SynchronizeNostrEventRequest,
        synchronizationRelayURLs: List<String>
    )

    suspend fun saveNostrEvent(
        nostrEvent: NostrEvent,
        negentropySynchronizeRequest: NegentropySynchronizeRequest,
        synchronizationRelayURLs: List<String>
    )

    suspend fun queueSynchronizeNostrEvent(
        synchronizeNostrEventRequests: List<SynchronizeNostrEventRequest>,
    )

    suspend fun queueNegentropySynchronizeRequest(
        negentropySynchronizeRequests: List<NegentropySynchronizeRequest>
    )

    suspend fun observeSynchronizeNostrEventRequestByPurposeAndStatusCount(purpose: String): Flow<Int>


    suspend fun getNostrEvent(publicKey: HexKey, kind: Kind): NostrEvent?

    suspend fun observeNostrFeed(): Flow<List<LocalNostrEvent>>

    suspend fun observeNostrFeed(synchronizationFilter: SynchronizationFilter): Flow<List<LocalNostrEvent>>

    suspend fun getNostrFeedIds(
        synchronizationFilters: Array<SynchronizationFilter>,
        applyLimits: Boolean = true
    ): List<NostrEvent>

    suspend fun observeLocalNostrEventById(nostrEventId: String): Flow<LocalNostrEvent?>

    suspend fun searchableProfiles(): List<Profile>

    suspend fun filterProfiles(searchTerm: String): List<LocalProfile>

    suspend fun observeProfileWithFollowers(publicKey: String): Flow<LocalProfileWithFollowers?>

    suspend fun observeProfileWithFollowing(publicKey: String): Flow<LocalProfileWithFollowing?>

    suspend fun observeChatRoomsByPublicKey(publicKey: String): Flow<List<LocalChatRoom>>

    suspend fun getRecentSearches(): List<RecentSearch>

    suspend fun removeRecentSearch(recentSearch: RecentSearch)

    suspend fun saveSearchQuery(query: String, synchronizationFilterArray: SynchronizationFilterArray? = null)

    suspend fun scheduleBroadcastNostrEventRequests(broadcastNostrEventRequests: List<BroadcastNostrEventRequest>)

    companion object {
        val NO_OP_NOSTR_REPOSITORY = object : NostrRepository {
            override suspend fun observeProfile(publicKey: HexKey): Flow<LocalAccount?> {
                TODO("Not yet implemented")
            }

            override suspend fun observeRelation(
                publicKey: HexKey,
                activePublicKey: HexKey
            ): Flow<List<Connection>> {
                TODO("Not yet implemented")
            }

            override suspend fun deleteConnection(connection: Connection) {
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

            override suspend fun observePendingNegentropySynchronizeRequests(): Flow<NegentropySynchronizeRequest?> {
                TODO("Not yet implemented")
            }

            override suspend fun createNewProfile(
                publicKey: HexKey,
                name: String?,
                biography: String?
            ) {
            }

            override suspend fun signInToProfile(publicKey: HexKey) {
                TODO("Not yet implemented")
            }

            override suspend fun createNewTextNote(
                publicKey: HexKey,
                mentionedPublicKeys: List<HexKey>,
                textInput: String,
                inReplyToNostrEvent: LocalNostrEvent?,
                quotedNostrEvent: LocalNostrEvent?,
                onError: () -> Unit,
                onSuccess: () -> Unit
            ) {
                TODO("Not yet implemented")
            }

            override suspend fun saveUnsignedNostrEvent(unsignedNostrEvent: UnsignedNostrEvent): UnsignedNostrEvent? {
                return null
            }

            override suspend fun saveUnsignedNostrEvents(unsignedNostrEvents: List<UnsignedNostrEvent>) {
                TODO("Not yet implemented")
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

            override suspend fun negentropySynchronizeRequestProcessed(negentropySynchronizeRequest: NegentropySynchronizeRequest) {
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

            override suspend fun saveNostrEvent(
                nostrEvent: NostrEvent,
                negentropySynchronizeRequest: NegentropySynchronizeRequest,
                synchronizationRelayURLs: List<String>
            ) {
                TODO("Not yet implemented")
            }

            override suspend fun queueSynchronizeNostrEvent(synchronizeNostrEventRequests: List<SynchronizeNostrEventRequest>) {
                TODO("Not yet implemented")
            }

            override suspend fun queueNegentropySynchronizeRequest(negentropySynchronizeRequests: List<NegentropySynchronizeRequest>) {
                TODO("Not yet implemented")
            }

            override suspend fun observeSynchronizeNostrEventRequestByPurposeAndStatusCount(purpose: String): Flow<Int> {
                TODO("Not yet implemented")
            }

            override suspend fun getNostrEvent(
                publicKey: HexKey,
                kind: Kind
            ): NostrEvent? {
                TODO("Not yet implemented")
            }

            override suspend fun observeNostrFeed(): Flow<List<LocalNostrEvent>> {
                TODO("Not yet implemented")
            }

            override suspend fun observeNostrFeed(synchronizationFilter: SynchronizationFilter): Flow<List<LocalNostrEvent>> {
                TODO("Not yet implemented")
            }

            override suspend fun getNostrFeedIds(
                synchronizationFilters: Array<SynchronizationFilter>,
                applyLimits: Boolean
            ): List<NostrEvent> {
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

            override suspend fun observeProfileWithFollowers(publicKey: String): Flow<LocalProfileWithFollowers?> {
                TODO("Not yet implemented")
            }

            override suspend fun observeProfileWithFollowing(publicKey: String): Flow<LocalProfileWithFollowing?> {
                TODO("Not yet implemented")
            }

            override suspend fun observeChatRoomsByPublicKey(publicKey: String): Flow<List<LocalChatRoom>> {
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

            override suspend fun scheduleBroadcastNostrEventRequests(broadcastNostrEventRequests: List<BroadcastNostrEventRequest>) {
                TODO("Not yet implemented")
            }
        }
    }
}