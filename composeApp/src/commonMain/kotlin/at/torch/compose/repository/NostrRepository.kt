package at.torch.compose.repository

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Kind
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import kotlinx.coroutines.flow.Flow

interface NostrRepository {
    suspend fun observeProfile(publicKey: HexKey): Flow<at.torch.compose.database.model.intermdiate.LocalAccount?>

    suspend fun observeRelation(publicKey: HexKey, activePublicKey: HexKey): Flow<List<at.torch.compose.database.model.Connection>>

    suspend fun deleteConnection(connection: at.torch.compose.database.model.Connection)

    suspend fun observeUnsignedNostrEvents(publicKey: HexKey): Flow<at.torch.compose.database.model.UnsignedNostrEvent?>

    suspend fun observePendingBroadcastNostrEventRequests(): Flow<List<at.torch.compose.database.model.intermdiate.LocalBroadcastNostrEventRequest>>

    suspend fun observePendingSynchronizeNostrEventRequests(): Flow<at.torch.compose.database.model.SynchronizeNostrEventRequest?>

    suspend fun observePendingNegentropySynchronizeRequests(): Flow<at.torch.compose.database.model.NegentropySynchronizeRequest?>

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
        inReplyToNostrEvent: at.torch.compose.database.model.intermdiate.LocalNostrEvent?,
        quotedNostrEvent: at.torch.compose.database.model.intermdiate.LocalNostrEvent?,
        onError: () -> Unit,
        onSuccess: () -> Unit
    )

    suspend fun saveUnsignedNostrEvent(unsignedNostrEvent: at.torch.compose.database.model.UnsignedNostrEvent): at.torch.compose.database.model.UnsignedNostrEvent?

    suspend fun saveUnsignedNostrEvents(unsignedNostrEvents: List<at.torch.compose.database.model.UnsignedNostrEvent>)

    suspend fun wipeDatabase()

    suspend fun publishNostrEvent(
        unsignedNostrEvent: at.torch.compose.database.model.UnsignedNostrEvent,
        nostrEvent: at.torch.compose.database.model.NostrEvent,
        relayURLs: List<String> = emptyList(),
        activeKeyPair: KeyPair
    )

    suspend fun broadcastProcessed(broadcastNostrEventRequest: at.torch.compose.database.model.BroadcastNostrEventRequest, status: String = "processing")

    suspend fun synchronizeNostrEventRequestProcessed(synchronizeNostrEventRequest: at.torch.compose.database.model.SynchronizeNostrEventRequest)

    suspend fun negentropySynchronizeRequestProcessed(negentropySynchronizeRequest: at.torch.compose.database.model.NegentropySynchronizeRequest)

    suspend fun saveBroadcastReceipt(broadcastNostrEventReceipt: at.torch.compose.database.model.BroadcastNostrEventReceipt)

    /**
     * Save nostrEvent from relays (and index for local viewing)...
     */
    suspend fun saveNostrEvent(
        nostrEvent: at.torch.compose.database.model.NostrEvent,
        synchronizeNostrEventRequest: at.torch.compose.database.model.SynchronizeNostrEventRequest,
        synchronizationRelayURLs: List<String>,
        activeKeyPair: KeyPair
    )

    suspend fun saveNostrEvent(
        nostrEvent: at.torch.compose.database.model.NostrEvent,
        negentropySynchronizeRequest: at.torch.compose.database.model.NegentropySynchronizeRequest,
        synchronizationRelayURLs: List<String>,
        activeKeyPair: KeyPair
    )

    suspend fun queueSynchronizeNostrEvent(
        synchronizeNostrEventRequests: List<at.torch.compose.database.model.SynchronizeNostrEventRequest>,
    )

    suspend fun queueNegentropySynchronizeRequest(
        negentropySynchronizeRequests: List<at.torch.compose.database.model.NegentropySynchronizeRequest>
    )

    suspend fun observeSynchronizeNostrEventRequestByPurposeAndStatusCount(purpose: String): Flow<Int>


    suspend fun getNostrEvent(publicKey: HexKey, kind: Kind): at.torch.compose.database.model.NostrEvent?

    suspend fun observeNostrFeed(): Flow<List<at.torch.compose.database.model.intermdiate.LocalNostrEvent>>

    suspend fun observeNostrFeed(synchronizationFilter: at.torch.compose.database.model.types.SynchronizationFilter): Flow<List<at.torch.compose.database.model.intermdiate.LocalNostrEvent>>

    suspend fun getNostrFeedIds(
        synchronizationFilters: Array<at.torch.compose.database.model.types.SynchronizationFilter>,
        applyLimits: Boolean = true
    ): List<at.torch.compose.database.model.NostrEvent>

    suspend fun observeLocalNostrEventById(nostrEventId: String): Flow<at.torch.compose.database.model.intermdiate.LocalNostrEvent?>

    suspend fun searchableProfiles(): List<at.torch.compose.database.model.Profile>

    suspend fun filterProfiles(searchTerm: String): List<at.torch.compose.database.model.intermdiate.LocalProfile>

    suspend fun observeProfileWithFollowers(publicKey: String): Flow<at.torch.compose.database.model.intermdiate.LocalProfileWithFollowers?>

    suspend fun observeProfileWithFollowing(publicKey: String): Flow<at.torch.compose.database.model.intermdiate.LocalProfileWithFollowing?>

    suspend fun getRecentSearches(): List<at.torch.compose.database.model.RecentSearch>

    suspend fun removeRecentSearch(recentSearch: at.torch.compose.database.model.RecentSearch)

    suspend fun saveSearchQuery(query: String, synchronizationFilterArray: at.torch.compose.database.model.typealiases.SynchronizationFilterArray? = null)

    suspend fun scheduleBroadcastNostrEventRequests(broadcastNostrEventRequests: List<at.torch.compose.database.model.BroadcastNostrEventRequest>)

    companion object {
        val NO_OP_NOSTR_REPOSITORY = object : NostrRepository {
            override suspend fun observeProfile(publicKey: HexKey): Flow<at.torch.compose.database.model.intermdiate.LocalAccount?> {
                TODO("Not yet implemented")
            }

            override suspend fun observeRelation(
                publicKey: HexKey,
                activePublicKey: HexKey
            ): Flow<List<at.torch.compose.database.model.Connection>> {
                TODO("Not yet implemented")
            }

            override suspend fun deleteConnection(connection: at.torch.compose.database.model.Connection) {
                TODO("Not yet implemented")
            }

            override suspend fun observeUnsignedNostrEvents(publicKey: HexKey): Flow<at.torch.compose.database.model.UnsignedNostrEvent?> {
                TODO("Not yet implemented")
            }

            override suspend fun observePendingBroadcastNostrEventRequests(): Flow<List<at.torch.compose.database.model.intermdiate.LocalBroadcastNostrEventRequest>> {
                TODO("Not yet implemented")
            }

            override suspend fun observePendingSynchronizeNostrEventRequests(): Flow<at.torch.compose.database.model.SynchronizeNostrEventRequest?> {
                TODO("Not yet implemented")
            }

            override suspend fun observePendingNegentropySynchronizeRequests(): Flow<at.torch.compose.database.model.NegentropySynchronizeRequest?> {
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
                inReplyToNostrEvent: at.torch.compose.database.model.intermdiate.LocalNostrEvent?,
                quotedNostrEvent: at.torch.compose.database.model.intermdiate.LocalNostrEvent?,
                onError: () -> Unit,
                onSuccess: () -> Unit
            ) {
                TODO("Not yet implemented")
            }

            override suspend fun saveUnsignedNostrEvent(unsignedNostrEvent: at.torch.compose.database.model.UnsignedNostrEvent): at.torch.compose.database.model.UnsignedNostrEvent? {
                return null
            }

            override suspend fun saveUnsignedNostrEvents(unsignedNostrEvents: List<at.torch.compose.database.model.UnsignedNostrEvent>) {
                TODO("Not yet implemented")
            }

            override suspend fun wipeDatabase() {
            }

            override suspend fun publishNostrEvent(
                unsignedNostrEvent: at.torch.compose.database.model.UnsignedNostrEvent,
                nostrEvent: at.torch.compose.database.model.NostrEvent,
                relayURLs: List<String>,
                activeKeyPair: KeyPair
            ) {

            }

            override suspend fun broadcastProcessed(
                broadcastNostrEventRequest: at.torch.compose.database.model.BroadcastNostrEventRequest,
                status: String
            ) {
                TODO("Not yet implemented")
            }

            override suspend fun synchronizeNostrEventRequestProcessed(synchronizeNostrEventRequest: at.torch.compose.database.model.SynchronizeNostrEventRequest) {
                TODO("Not yet implemented")
            }

            override suspend fun negentropySynchronizeRequestProcessed(negentropySynchronizeRequest: at.torch.compose.database.model.NegentropySynchronizeRequest) {
                TODO("Not yet implemented")
            }

            override suspend fun saveBroadcastReceipt(broadcastNostrEventReceipt: at.torch.compose.database.model.BroadcastNostrEventReceipt) {
                TODO("Not yet implemented")
            }

            override suspend fun saveNostrEvent(
                nostrEvent: at.torch.compose.database.model.NostrEvent,
                synchronizeNostrEventRequest: at.torch.compose.database.model.SynchronizeNostrEventRequest,
                synchronizationRelayURLs: List<String>,
                activeKeyPair: KeyPair
            ) {
                TODO("Not yet implemented")
            }

            override suspend fun saveNostrEvent(
                nostrEvent: at.torch.compose.database.model.NostrEvent,
                negentropySynchronizeRequest: at.torch.compose.database.model.NegentropySynchronizeRequest,
                synchronizationRelayURLs: List<String>,
                activeKeyPair: KeyPair
            ) {
                TODO("Not yet implemented")
            }

            override suspend fun queueSynchronizeNostrEvent(synchronizeNostrEventRequests: List<at.torch.compose.database.model.SynchronizeNostrEventRequest>) {
                TODO("Not yet implemented")
            }

            override suspend fun queueNegentropySynchronizeRequest(negentropySynchronizeRequests: List<at.torch.compose.database.model.NegentropySynchronizeRequest>) {
                TODO("Not yet implemented")
            }

            override suspend fun observeSynchronizeNostrEventRequestByPurposeAndStatusCount(purpose: String): Flow<Int> {
                TODO("Not yet implemented")
            }

            override suspend fun getNostrEvent(
                publicKey: HexKey,
                kind: Kind
            ): at.torch.compose.database.model.NostrEvent? {
                TODO("Not yet implemented")
            }

            override suspend fun observeNostrFeed(): Flow<List<at.torch.compose.database.model.intermdiate.LocalNostrEvent>> {
                TODO("Not yet implemented")
            }

            override suspend fun observeNostrFeed(synchronizationFilter: at.torch.compose.database.model.types.SynchronizationFilter): Flow<List<at.torch.compose.database.model.intermdiate.LocalNostrEvent>> {
                TODO("Not yet implemented")
            }

            override suspend fun getNostrFeedIds(
                synchronizationFilters: Array<at.torch.compose.database.model.types.SynchronizationFilter>,
                applyLimits: Boolean
            ): List<at.torch.compose.database.model.NostrEvent> {
                TODO("Not yet implemented")
            }

            override suspend fun observeLocalNostrEventById(nostrEventId: String): Flow<at.torch.compose.database.model.intermdiate.LocalNostrEvent?> {
                TODO("Not yet implemented")
            }

            override suspend fun searchableProfiles(): List<at.torch.compose.database.model.Profile> {
                TODO("Not yet implemented")
            }

            override suspend fun filterProfiles(searchTerm: String): List<at.torch.compose.database.model.intermdiate.LocalProfile> {
                TODO("Not yet implemented")
            }

            override suspend fun observeProfileWithFollowers(publicKey: String): Flow<at.torch.compose.database.model.intermdiate.LocalProfileWithFollowers?> {
                TODO("Not yet implemented")
            }

            override suspend fun observeProfileWithFollowing(publicKey: String): Flow<at.torch.compose.database.model.intermdiate.LocalProfileWithFollowing?> {
                TODO("Not yet implemented")
            }

            override suspend fun getRecentSearches(): List<at.torch.compose.database.model.RecentSearch> {
                TODO("Not yet implemented")
            }

            override suspend fun removeRecentSearch(recentSearch: at.torch.compose.database.model.RecentSearch) {
                TODO("Not yet implemented")
            }

            override suspend fun saveSearchQuery(
                query: String,
                synchronizationFilterArray: at.torch.compose.database.model.typealiases.SynchronizationFilterArray?
            ) {
                TODO("Not yet implemented")
            }

            override suspend fun scheduleBroadcastNostrEventRequests(broadcastNostrEventRequests: List<at.torch.compose.database.model.BroadcastNostrEventRequest>) {
                TODO("Not yet implemented")
            }
        }
    }
}