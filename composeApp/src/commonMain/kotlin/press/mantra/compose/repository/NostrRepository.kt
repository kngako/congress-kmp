package press.mantra.compose.repository

import press.mantra.compose.database.model.BroadcastNostrEventReceipt
import press.mantra.compose.database.model.BroadcastNostrEventRequest
import press.mantra.compose.database.model.Connection
import press.mantra.compose.database.model.NegentropySynchronizeRequest
import press.mantra.compose.database.model.NostrEvent
import press.mantra.compose.database.model.Profile
import press.mantra.compose.database.model.RecentSearch
import press.mantra.compose.database.model.SynchronizeNostrEventRequest
import press.mantra.compose.database.model.UnsignedNostrEvent
import press.mantra.compose.database.model.intermdiate.LocalAccount
import press.mantra.compose.database.model.intermdiate.LocalBroadcastNostrEventRequest
import press.mantra.compose.database.model.intermdiate.LocalNostrEvent
import press.mantra.compose.database.model.intermdiate.LocalProfile
import press.mantra.compose.database.model.intermdiate.LocalProfileWithFollowers
import press.mantra.compose.database.model.intermdiate.LocalProfileWithFollowing
import press.mantra.compose.database.model.types.SynchronizationFilter
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Kind
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface NostrRepository {
    suspend fun getLocalAccounts(): List<LocalAccount>

    suspend fun observeLocalAccount(publicKey: HexKey): Flow<LocalAccount?>

    suspend fun getProfileWithPublicKey(publicKey: HexKey): Profile?

    suspend fun observeProfileWithPublicKey(publicKey: HexKey): Flow<Profile?>

    suspend fun observeRelation(publicKey: HexKey, activePublicKey: HexKey): Flow<List<Connection>>

    suspend fun deleteConnection(connection: Connection)

    /**
     * The whole unsigned backlog for [publicKey], lowest kind first.
     *
     * See `UnsignedNostrEventDao.observeUnsignedNostrEvents`: a row that cannot be
     * published used to be the entire queue. Callers work through it in order, and a row
     * that fails costs only itself.
     */
    suspend fun observeUnsignedNostrEvents(publicKey: HexKey): Flow<List<UnsignedNostrEvent>>

    suspend fun observePendingBroadcastNostrEventRequests(): Flow<LocalBroadcastNostrEventRequest?>

    /** The head of the fetch queue for one identity: its own requests and the ones nobody owns. */
    suspend fun observePendingSynchronizeNostrEventRequests(ownerPublicKey: HexKey): Flow<SynchronizeNostrEventRequest?>

    suspend fun observePendingNegentropySynchronizeRequests(ownerPublicKey: HexKey): Flow<NegentropySynchronizeRequest?>

    /**
     * Opens every gift wrap addressed to [activeKeyPair] that the device stored and could
     * not open at the time. Run once per activation, before the live inbox is opened.
     * Returns how many were opened.
     */
    suspend fun reopenInbox(activeKeyPair: KeyPair): Int

    suspend fun createNewProfile(
        publicKey: HexKey,
        name: String?,
        biography: String?,
        onCompletion: suspend () -> Unit
    )

    suspend fun signInToProfile(
        publicKey: HexKey
    )

    /**
     * The exit from a sign-in whose search found nothing: the key already exists, only
     * the events do not. Gives [publicKey] the same bootstrap [createNewProfile] gives a
     * fresh key, with the kind 0 written *over* the placeholder [signInToProfile] planted
     * -- see the implementation for why over rather than beside.
     */
    suspend fun setUpProfileForExistingKey(
        publicKey: HexKey,
        placeholderUnsignedNostrEventId: Long,
        name: String?,
        biography: String?,
    )

    /**
     * Stops treating [publicKey] as one of this device's own: its unsigned rows go, and
     * with them the requests that hang off them. What it published, and its profile, stay
     * cached as anyone else's would.
     */
    suspend fun forgetLocalAccount(publicKey: HexKey)

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

    /**
     * Move broadcasts a previous process left in "processing"/"failed" back to "pending" so an
     * interrupted or rejected publish is retried instead of stranded. Returns the row count.
     */
    suspend fun requeueStaleBroadcastNostrEventRequests(): Int

    suspend fun synchronizeNostrEventRequestProcessed(synchronizeNostrEventRequest: SynchronizeNostrEventRequest)

    /**
     * The relay has finished answering [synchronizeNostrEventRequest] -- EOSE, CLOSED or
     * the bounded timeout -- and the request is still `sent`, meaning nothing arrived. Until
     * this existed "still searching" and "searched, found nothing" were the same row.
     */
    suspend fun synchronizeNostrEventRequestCompleted(synchronizeNostrEventRequest: SynchronizeNostrEventRequest)

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

    /**
     * Save a nostrEvent that arrived on a subscription rather than in answer to a queued
     * request.
     *
     * The other two overloads take the request row an event was fetched for, because they
     * also have to record which request produced it and flip that row to "processed". A
     * live subscription has no row and never finishes, so it carries the relay and level
     * itself.
     */
    suspend fun saveNostrEvent(
        nostrEvent: NostrEvent,
        relayURL: String,
        synchronizationRelayURLs: List<String>,
        level: Int,
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

    suspend fun searchableProfiles(
        excludingPublicKeys: Array<HexKey> = emptyArray()
    ): List<Profile>

    suspend fun filterProfiles(searchTerm: String): List<LocalProfile>

    suspend fun observeProfileWithFollowers(publicKey: String): Flow<LocalProfileWithFollowers?>

    suspend fun observeProfileWithFollowing(publicKey: String): Flow<LocalProfileWithFollowing?>

    suspend fun getRecentSearches(): List<RecentSearch>

    suspend fun removeRecentSearch(recentSearch: RecentSearch)

    suspend fun saveSearchQuery(query: String, synchronizationFilterArray: press.mantra.compose.database.model.typealiases.SynchronizationFilterArray? = null)

    suspend fun rescheduleBroadcastNostrEventRequests(broadcastNostrEventRequests: List<BroadcastNostrEventRequest>)

    companion object {
        /**
         * A repository with nothing behind it: every question answers "nothing here",
         * every write goes nowhere, and **no member throws**.
         *
         * Previews and test fixtures are the only callers, and both want inertness. It is
         * written out here because these members were once `TODO()`, and a `TODO()` in a
         * no-op is not a loud failure -- it is a silent misattribution. Callers reach most
         * of them from a `viewModelScope.launch(Dispatchers.IO)` that nobody awaits, so the
         * `NotImplementedError` escapes to the platform handler, is stashed by
         * kotlinx-coroutines-test's `ExceptionCollector`, and is re-thrown as
         * `UncaughtExceptionsBeforeTest` inside *the next* `runTest` -- under that test's
         * name, with the real culprit buried as a suppressed cause. One `TODO()` here
         * (`queueNegentropySynchronizeRequest`, reached from
         * `ChatMessageListViewModel.scheduleSynchronization`) failed `NavigationRoutingTest`
         * four times a run for a call that test never made.
         *
         * So the rules for a member added here, which the other three no-op repositories
         * follow too:
         *
         *  - **Answer the call, never stub it out.** `NoOpRepositoryTotalityJvmTest` reads
         *    these four objects and fails if an unimplemented stub reappears in one.
         *  - **A flow emits its empty value once** -- `flowOf(null)`, `flowOf(emptyList())` --
         *    rather than `flow { }`. A screen collecting an empty flow waits in Loading
         *    forever; one collecting `flowOf(null)` renders its empty state, which is what
         *    a preview is for.
         *  - **A callback is not invoked.** Nothing happened, and calling `onCompletion` or
         *    `onSuccess` would say it did -- letting a test pass on the fixture rather than
         *    on the code under test.
         *  - **Throwing is never the way to be loud.** A member that a test must not call is
         *    the test's assertion to make, not the fixture's: override it with a recorder and
         *    assert it stayed untouched. An exception thrown here lands on whichever test
         *    runs next.
         *
         * A test that needs a real answer from one member overrides just that one:
         * `object : NostrRepository by NO_OP_NOSTR_REPOSITORY { … }`.
         */
        val NO_OP_NOSTR_REPOSITORY: NostrRepository = object : NostrRepository {
            override suspend fun getLocalAccounts(): List<LocalAccount> = emptyList()

            override suspend fun observeLocalAccount(publicKey: HexKey): Flow<LocalAccount?> =
                flowOf(null)

            override suspend fun getProfileWithPublicKey(publicKey: HexKey): Profile? = null

            override suspend fun observeProfileWithPublicKey(publicKey: HexKey): Flow<Profile?> =
                flowOf(null)

            override suspend fun observeRelation(
                publicKey: HexKey,
                activePublicKey: HexKey
            ): Flow<List<Connection>> = flowOf(emptyList())

            override suspend fun deleteConnection(connection: Connection) = Unit

            override suspend fun observeUnsignedNostrEvents(publicKey: HexKey): Flow<List<UnsignedNostrEvent>> =
                flowOf(emptyList())

            override suspend fun observePendingBroadcastNostrEventRequests(): Flow<LocalBroadcastNostrEventRequest?> =
                flowOf(null)

            override suspend fun reopenInbox(activeKeyPair: KeyPair): Int = 0

            override suspend fun observePendingSynchronizeNostrEventRequests(ownerPublicKey: HexKey): Flow<SynchronizeNostrEventRequest?> =
                flowOf(null)

            override suspend fun observePendingNegentropySynchronizeRequests(ownerPublicKey: HexKey): Flow<NegentropySynchronizeRequest?> =
                flowOf(null)

            // onCompletion is deliberately not called: no profile was created, and a
            // fixture that reports completion is a fixture a test can pass against.
            override suspend fun createNewProfile(
                publicKey: HexKey,
                name: String?,
                biography: String?,
                onCompletion: suspend () -> Unit
            ) = Unit

            override suspend fun signInToProfile(publicKey: HexKey) = Unit

            override suspend fun setUpProfileForExistingKey(
                publicKey: HexKey,
                placeholderUnsignedNostrEventId: Long,
                name: String?,
                biography: String?,
            ) = Unit

            override suspend fun forgetLocalAccount(publicKey: HexKey) = Unit

            // Neither onSuccess nor onError: the note was not written, and it did not fail
            // to be written either -- there was nowhere to write it. A test that wants
            // either branch overrides this member and calls the one it is testing.
            override suspend fun createNewTextNote(
                publicKey: HexKey,
                mentionedPublicKeys: List<HexKey>,
                textInput: String,
                inReplyToNostrEvent: LocalNostrEvent?,
                quotedNostrEvent: LocalNostrEvent?,
                onError: () -> Unit,
                onSuccess: () -> Unit
            ) = Unit

            override suspend fun saveUnsignedNostrEvent(unsignedNostrEvent: UnsignedNostrEvent): UnsignedNostrEvent? =
                null

            override suspend fun saveUnsignedNostrEvents(unsignedNostrEvents: List<UnsignedNostrEvent>) = Unit

            override suspend fun wipeDatabase() = Unit

            override suspend fun publishNostrEvent(
                unsignedNostrEvent: UnsignedNostrEvent,
                nostrEvent: NostrEvent,
                relayURLs: List<String>,
                activeKeyPair: KeyPair
            ) = Unit

            override suspend fun broadcastProcessed(
                broadcastNostrEventRequest: BroadcastNostrEventRequest,
                status: String
            ) = Unit

            override suspend fun requeueStaleBroadcastNostrEventRequests(): Int = 0

            override suspend fun synchronizeNostrEventRequestProcessed(synchronizeNostrEventRequest: SynchronizeNostrEventRequest) = Unit

            override suspend fun synchronizeNostrEventRequestCompleted(synchronizeNostrEventRequest: SynchronizeNostrEventRequest) = Unit

            override suspend fun negentropySynchronizeRequestProcessed(negentropySynchronizeRequest: NegentropySynchronizeRequest) = Unit

            override suspend fun saveBroadcastReceipt(broadcastNostrEventReceipt: BroadcastNostrEventReceipt) = Unit

            override suspend fun saveNostrEvent(
                nostrEvent: NostrEvent,
                synchronizeNostrEventRequest: SynchronizeNostrEventRequest,
                synchronizationRelayURLs: List<String>,
                activeKeyPair: KeyPair
            ) = Unit

            override suspend fun saveNostrEvent(
                nostrEvent: NostrEvent,
                negentropySynchronizeRequest: NegentropySynchronizeRequest,
                synchronizationRelayURLs: List<String>,
                activeKeyPair: KeyPair
            ) = Unit

            override suspend fun saveNostrEvent(
                nostrEvent: NostrEvent,
                relayURL: String,
                synchronizationRelayURLs: List<String>,
                level: Int,
                activeKeyPair: KeyPair
            ) = Unit

            override suspend fun queueSynchronizeNostrEvent(synchronizeNostrEventRequests: List<SynchronizeNostrEventRequest>) = Unit

            override suspend fun queueNegentropySynchronizeRequest(negentropySynchronizeRequests: List<NegentropySynchronizeRequest>) = Unit

            override suspend fun observeSynchronizeNostrEventRequestByPurposeAndStatusCount(purpose: String): Flow<Int> =
                flowOf(0)

            override suspend fun getNostrEvent(
                publicKey: HexKey,
                kind: Kind
            ): NostrEvent? = null

            override suspend fun observeNostrFeed(): Flow<List<LocalNostrEvent>> = flowOf(emptyList())

            override suspend fun observeNostrFeed(synchronizationFilter: SynchronizationFilter): Flow<List<LocalNostrEvent>> =
                flowOf(emptyList())

            override suspend fun getNegentropicNostrFeedIds(
                synchronizationFilters: Array<SynchronizationFilter>,
                applyLimits: Boolean
            ): List<NostrEvent> = emptyList()

            override suspend fun observeLocalNostrEventById(nostrEventId: String): Flow<LocalNostrEvent?> =
                flowOf(null)

            override suspend fun searchableProfiles(excludingPublicKeys: Array<HexKey>): List<Profile> =
                emptyList()

            override suspend fun filterProfiles(searchTerm: String): List<LocalProfile> = emptyList()

            override suspend fun observeProfileWithFollowers(publicKey: String): Flow<LocalProfileWithFollowers?> =
                flowOf(null)

            override suspend fun observeProfileWithFollowing(publicKey: String): Flow<LocalProfileWithFollowing?> =
                flowOf(null)

            override suspend fun getRecentSearches(): List<RecentSearch> = emptyList()

            override suspend fun removeRecentSearch(recentSearch: RecentSearch) = Unit

            override suspend fun saveSearchQuery(
                query: String,
                synchronizationFilterArray: press.mantra.compose.database.model.typealiases.SynchronizationFilterArray?
            ) = Unit

            override suspend fun rescheduleBroadcastNostrEventRequests(broadcastNostrEventRequests: List<BroadcastNostrEventRequest>) = Unit
        }
    }
}
