package at.torch.compose.repository

import at.torch.compose.database.model.BroadcastNostrEventReceipt
import at.torch.compose.database.model.BroadcastNostrEventRequest
import at.torch.compose.database.model.Connection
import at.torch.compose.database.model.NegentropySynchronizeRequest
import at.torch.compose.database.model.NostrEvent
import at.torch.compose.database.model.Profile
import at.torch.compose.database.model.RecentSearch
import at.torch.compose.database.model.SynchronizeNostrEventRequest
import at.torch.compose.database.model.UnsignedNostrEvent
import at.torch.compose.database.model.intermdiate.LocalAccount
import at.torch.compose.database.model.intermdiate.LocalBroadcastNostrEventRequest
import at.torch.compose.database.model.intermdiate.LocalNostrEvent
import at.torch.compose.database.model.intermdiate.LocalProfile
import at.torch.compose.database.model.intermdiate.LocalProfileWithFollowers
import at.torch.compose.database.model.intermdiate.LocalProfileWithFollowing
import at.torch.compose.database.model.types.SynchronizationFilter
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Kind
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import kotlinx.coroutines.flow.Flow

interface NostrRepository {
    suspend fun getLocalAccounts(): List<LocalAccount>

    suspend fun observeProfile(publicKey: HexKey): Flow<LocalAccount?>

    suspend fun observeRelation(publicKey: HexKey, activePublicKey: HexKey): Flow<List<Connection>>

    suspend fun deleteConnection(connection: Connection)

    suspend fun observeUnsignedNostrEvents(publicKey: HexKey): Flow<UnsignedNostrEvent?>

    suspend fun observePendingBroadcastNostrEventRequests(): Flow<LocalBroadcastNostrEventRequest?>

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
        relayURLs: List<String> = emptyList(),
        activeKeyPair: KeyPair
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
        synchronizationRelayURLs: List<String>,
        activeKeyPair: KeyPair
    )

    suspend fun saveNostrEvent(
        nostrEvent: NostrEvent,
        negentropySynchronizeRequest: NegentropySynchronizeRequest,
        synchronizationRelayURLs: List<String>,
        activeKeyPair: KeyPair
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

    suspend fun getNegentropicNostrFeedIds(
        synchronizationFilters: Array<SynchronizationFilter>,
        applyLimits: Boolean = true
    ): List<NostrEvent>

    suspend fun observeLocalNostrEventById(nostrEventId: String): Flow<LocalNostrEvent?>

    suspend fun searchableProfiles(): List<Profile>

    suspend fun filterProfiles(searchTerm: String): List<LocalProfile>

    suspend fun observeProfileWithFollowers(publicKey: String): Flow<LocalProfileWithFollowers?>

    suspend fun observeProfileWithFollowing(publicKey: String): Flow<LocalProfileWithFollowing?>

    suspend fun getRecentSearches(): List<RecentSearch>

    suspend fun removeRecentSearch(recentSearch: RecentSearch)

    suspend fun saveSearchQuery(query: String, synchronizationFilterArray: at.torch.compose.database.model.typealiases.SynchronizationFilterArray? = null)

    suspend fun rescheduleBroadcastNostrEventRequests(broadcastNostrEventRequests: List<BroadcastNostrEventRequest>)

    companion object {
        val NO_OP_NOSTR_REPOSITORY = object : NostrRepository {
            override suspend fun getLocalAccounts(): List<LocalAccount> {
                TODO("Not yet implemented")
            }

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

            override suspend fun observePendingBroadcastNostrEventRequests(): Flow<LocalBroadcastNostrEventRequest> {
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
                TODO("")
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
                relayURLs: List<String>,
                activeKeyPair: KeyPair
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
                synchronizationRelayURLs: List<String>,
                activeKeyPair: KeyPair
            ) {
                TODO("Not yet implemented")
            }

            override suspend fun saveNostrEvent(
                nostrEvent: NostrEvent,
                negentropySynchronizeRequest: NegentropySynchronizeRequest,
                synchronizationRelayURLs: List<String>,
                activeKeyPair: KeyPair
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

            override suspend fun getNegentropicNostrFeedIds(
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

            override suspend fun getRecentSearches(): List<RecentSearch> {
                TODO("Not yet implemented")
            }

            override suspend fun removeRecentSearch(recentSearch: RecentSearch) {
                TODO("Not yet implemented")
            }

            override suspend fun saveSearchQuery(
                query: String,
                synchronizationFilterArray: at.torch.compose.database.model.typealiases.SynchronizationFilterArray?
            ) {
                TODO("Not yet implemented")
            }

            override suspend fun rescheduleBroadcastNostrEventRequests(broadcastNostrEventRequests: List<BroadcastNostrEventRequest>) {
                TODO("Not yet implemented")
            }
        }
    }
}