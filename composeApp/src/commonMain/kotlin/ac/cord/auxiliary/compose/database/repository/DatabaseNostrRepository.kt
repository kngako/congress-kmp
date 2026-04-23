package ac.cord.auxiliary.compose.database.repository

import ac.cord.auxiliary.compose.database.AuxDatabase
import ac.cord.auxiliary.compose.database.GENESIS_AT
import ac.cord.auxiliary.compose.database.model.BroadcastNostrEventReceipt
import ac.cord.auxiliary.compose.database.model.BroadcastNostrEventRequest
import ac.cord.auxiliary.compose.database.model.Connection
import ac.cord.auxiliary.compose.database.model.NostrEvent
import ac.cord.auxiliary.compose.database.model.Profile
import ac.cord.auxiliary.compose.database.model.RecentSearch
import ac.cord.auxiliary.compose.database.model.Relay
import ac.cord.auxiliary.compose.database.model.SynchronizeNostrEventRequest
import ac.cord.auxiliary.compose.database.model.UnsignedNostrEvent
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalBroadcastNostrEventRequest
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalNostrEvent
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalAccount
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalProfile
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalProfileWithFollowers
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalProfileWithFollowing
import ac.cord.auxiliary.compose.database.model.typealiases.SynchronizationFilterArray
import ac.cord.auxiliary.compose.database.model.types.SynchronizationFilter
import ac.cord.auxiliary.compose.nostr.Relays
import ac.cord.auxiliary.compose.repository.NostrRepository
import ac.cord.auxiliary.compose.repository.RelayRepository
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Kind
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip02FollowList.RelaySet
import com.vitorpamplona.quartz.nip02FollowList.tags.ContactTag
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip28PublicChat.list.ChannelListEvent
import com.vitorpamplona.quartz.nip31Alts.AltTag
import com.vitorpamplona.quartz.nip50Search.SearchRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.IndexerRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.RelayFeedsListEvent
import com.vitorpamplona.quartz.nip51Lists.tags.RelayTag
import com.vitorpamplona.quartz.nip57Zaps.LnZapEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlin.collections.emptyMap
import kotlin.collections.plus
import kotlin.time.Clock
import kotlin.time.Instant

class DatabaseNostrRepository(
    private val database: AuxDatabase
): NostrRepository, RelayRepository {
    companion object {
        const val TAG = "DatabaseNostrRepository"
    }

    val logger = Logger.withTag(TAG)

    override suspend fun observeProfile(publicKey: HexKey): Flow<LocalAccount?> {
        return database.unsignedNostrEventDao().observeProfile(publicKey)
    }

    override suspend fun observeRelation(
        publicKey: HexKey,
        activePublicKey: HexKey
    ): Flow<List<Connection>> {
        return database.connectionDao().observeRelation(
            alicePublicKey = publicKey,
            bobPublicKey = activePublicKey
        )
    }

    override suspend fun deleteConnection(connection: Connection) {
        database.connectionDao().delete(connection)
    }

    override suspend fun observeUnsignedNostrEvents(publicKey: HexKey): Flow<UnsignedNostrEvent?> {
        return database.unsignedNostrEventDao().observeUnsignedNostrEvents(publicKey)
    }

    override suspend fun observePendingBroadcastNostrEventRequests(): Flow<List<LocalBroadcastNostrEventRequest>> {
        return database.broadcastNostrEventRequestDao().observeBroadcastNostrEventRequestsByStatus("pending")
    }

    override suspend fun observePendingSynchronizeNostrEventRequests(): Flow<SynchronizeNostrEventRequest?> {
        return database.synchronizeNostrEventRequestDao().observeSynchronizeNostrEventRequestsByStatus("pending")
    }

    override suspend fun createNewProfile(
        publicKey: HexKey,
        name: String?,
        biography: String?,
        onError: () -> Unit,
        onProfileReady: (Profile) -> Unit,
        synchronizationRelayURLs: List<String>
    ) {
        val profileEventTemplate = MetadataEvent.createNew(
            name = name,
            about = biography,
        )

        val signedAt = if (name == null && biography == null) {
            // We are most likely try to sign in to an already existing block...
            GENESIS_AT
        } else {
            null
        }

        val unsignedNostrEvent = saveUnsignedNostrEvent(
            UnsignedNostrEvent(
                pubKey = publicKey,
                kind = profileEventTemplate.kind,
                createdAt = Instant.fromEpochSeconds(profileEventTemplate.createdAt),
                tags = profileEventTemplate.tags,
                content = profileEventTemplate.content,
                signedAt = signedAt // This will keep us from trying to sign this event... but we should automatically sync the npub profile
            )
        )

        if (unsignedNostrEvent == null) {
            onError.invoke()
        } else if (unsignedNostrEvent.kind == 0) {
            // Look busy for the onboarding screen
            delay(3_500)


            if (unsignedNostrEvent.signedAt == null) {
                // Save ContactListNostrEvent
                val followUsers = listOf(ContactTag(publicKey, null, null))
                val contactListTagList = listOf(AltTag.assemble(ContactListEvent.ALT)) +
                        followUsers.map { it.toTagArray() }

                saveUnsignedNostrEvent(
                    UnsignedNostrEvent(
                        pubKey = publicKey,
                        kind = ContactListEvent.KIND,
                        tags = contactListTagList.toTypedArray(),
                        content = RelaySet.assemble(
                            emptyMap()
                        )
                    )
                )

                // Save AdvertisedRelayListEvent
                saveUnsignedNostrEvent(
                    UnsignedNostrEvent(
                        pubKey = publicKey,
                        kind = AdvertisedRelayListEvent.KIND,
                        tags = Relays.DefaultNIP65List.map{ it.toTagArray() }.toTypedArray(),
                        content = ""
                    )
                )
                // Save ChatMessageRelayListEvent
                saveUnsignedNostrEvent(
                    UnsignedNostrEvent(
                        pubKey = publicKey,
                        kind = ChatMessageRelayListEvent.KIND,
                        tags = Relays.DefaultDMRelayList.map{ RelayTag.assemble(it) }
                            .plusElement(
                                AltTag.assemble("Relay list to receive private messages")
                            ).toTypedArray(),
                        content = ""
                    )
                )
                // Save SearchRelayListEvent
                saveUnsignedNostrEvent(
                    UnsignedNostrEvent(
                        pubKey = publicKey,
                        kind = SearchRelayListEvent.KIND,
                        tags = Relays.DefaultSearchRelayList.map{ RelayTag.assemble(it) }
                            .plus(
                                SearchRelayListEvent.ALT_TAG
                            ).toTypedArray(),
                        privateTags = emptyArray(),
                        content = ""
                    )
                )
                // Save IndexerRelayListEvent
                saveUnsignedNostrEvent(
                    UnsignedNostrEvent(
                        pubKey = publicKey,
                        kind = IndexerRelayListEvent.KIND,
                        tags = arrayOf(AltTag.assemble(IndexerRelayListEvent.ALT)),
                        privateTags = Relays.DefaultIndexerRelayList.map { RelayTag.assemble(it) }.toTypedArray() ,
                        content = ""
                    )
                )
                // Save ChannelListEvent
                saveUnsignedNostrEvent(
                    UnsignedNostrEvent(
                        pubKey = publicKey,
                        kind = ChannelListEvent.KIND,
                        tags = arrayOf(
                            AltTag.assemble(
                                ChannelListEvent.ALT
                            )
                        ),
                        privateTags = Relays.DefaultChannels.map { it.toTagArray() }.toTypedArray(),
                        content = ""
                    )
                )
                // Save RelayFeedsListEvent
                saveUnsignedNostrEvent(
                    UnsignedNostrEvent(
                        pubKey = publicKey,
                        kind = RelayFeedsListEvent.KIND,
                        tags = arrayOf(
                            AltTag.assemble(RelayFeedsListEvent.ALT)
                        ),
                        privateTags = Relays.DefaultGlobalRelays.map { RelayTag.assemble(it)}.toTypedArray(),
                        content = ""
                    )
                )
            }
        }
    }

    override suspend fun createNewTextNote(
        publicKey: HexKey,
        text: String,
        onError: () -> Unit,
        onSuccess: () -> Unit
    ) {
        val textNote = TextNoteEvent.build(
            note = text,
        )

        val unsignedNostrEvent = saveUnsignedNostrEvent(
            UnsignedNostrEvent(
                pubKey = publicKey,
                kind = textNote.kind,
                createdAt = Instant.fromEpochSeconds(textNote.createdAt),
                tags = textNote.tags,
                content = textNote.content
            )
        )

        if (unsignedNostrEvent == null) {
           onError.invoke()
        } else {
            onSuccess.invoke()
        }
    }

    override suspend fun saveUnsignedNostrEvent(unsignedNostrEvent: UnsignedNostrEvent): UnsignedNostrEvent? = try {
        // TODO: We can actually check the unsignedNostrEvent.pubKey for support... if it's writeable
        delay(2_100) // Just to make it look like we are doing serious work...
        unsignedNostrEvent.copy(
            id = database.unsignedNostrEventDao().upsert(unsignedNostrEvent)
        )
    } catch (e: Throwable) {
        logger.e("Failed to saveUnsignedNostrEvent", e)
        null
    }

    override suspend fun wipeDatabase() {
        database.wipeData()
    }

    override suspend fun publishNostrEvent(
        unsignedNostrEvent: UnsignedNostrEvent,
        nostrEvent: NostrEvent,
        relayURLs: List<String>
    ) {
        database.nostrDao().publishNostrEvent(
            unsignedNostrEvent,
            nostrEvent,
            relayURLs
        )
    }

    override suspend fun broadcastProcessed(
        broadcastNostrEventRequest: BroadcastNostrEventRequest,
        status: String
    ) {
        logger.d("Update local reference: $broadcastNostrEventRequest")
        database.broadcastNostrEventRequestDao().upsert(
            broadcastNostrEventRequest.copy(
                status = status
            )
        )
        logger.d("$status: $broadcastNostrEventRequest")
    }

    override suspend fun synchronizeNostrEventRequestProcessed(synchronizeNostrEventRequest: SynchronizeNostrEventRequest) {
        logger.i("Update local reference: $synchronizeNostrEventRequest")
        database.synchronizeNostrEventRequestDao().upsert(
            synchronizeNostrEventRequest.copy(
                status = "sent",
                updatedAt = Clock.System.now()
            )
        )
    }

    override suspend fun saveBroadcastReceipt(broadcastNostrEventReceipt: BroadcastNostrEventReceipt) {
        database.broadcastNostrEventReceiptDao().upsert(
            broadcastNostrEventReceipt
        )
    }

    override suspend fun saveNostrEvent(
        nostrEvent: NostrEvent,
        synchronizeNostrEventRequest: SynchronizeNostrEventRequest,
        synchronizationRelayURLs: List<String>
    ) {
        database.nostrDao().storeNostrEvent(
            nostrEvent,
            synchronizationRelayURLs = synchronizationRelayURLs,
            level = synchronizeNostrEventRequest.level
        )

        database.synchronizeNostrEventRequestDao().upsert(
            synchronizeNostrEventRequest.copy(
                nostrEventId = nostrEvent.id,
                status = "processed"
            )
        )
    }

    override suspend fun queueSynchronizeNostrEvent(
        synchronizeNostrEventRequests: List<SynchronizeNostrEventRequest>,
    ) {
        // Save synchronizationResult
        database.synchronizeNostrEventRequestDao().insert(
            synchronizeNostrEventRequests
        )
    }

    override suspend fun observeSynchronizeNostrEventRequestByPurposeAndStatusCount(purpose: String): Flow<Int> {
        return database.synchronizeNostrEventRequestDao().observeSynchronizeNostrEventRequestByPurposeAndStatusCount(
            "feed",
            listOf("pending", "processing")
        )
    }

    override suspend fun getNostrEvent(publicKey: HexKey, kind: Kind): NostrEvent? {
        return database.nostrEventDao().getNostrEventByPublicKeyAndKind(
            publicKey = publicKey,
            kind = kind
        )
    }

    override suspend fun observeNostrFeed(): Flow<List<LocalNostrEvent>> {
        return database.nostrEventDao().observeNostrEvents(
            kinds = arrayOf(
                TextNoteEvent.KIND,
                RepostEvent.KIND,
                ReactionEvent.KIND,
                LnZapEvent.KIND
            )
        )
    }

    override suspend fun observeNostrFeed(synchronizationFilter: SynchronizationFilter): Flow<List<LocalNostrEvent>> {

        return when {
            synchronizationFilter.kinds != null && synchronizationFilter.search != null -> {
                logger.d("observeFilteredNostrEvents: $synchronizationFilter")
                database.nostrEventDao().observeFilteredNostrEvents(
                    kinds = synchronizationFilter.kinds,
                    search = synchronizationFilter.search
                )
            }
            synchronizationFilter.kinds != null && synchronizationFilter.ids != null -> {
                logger.d("observeFilteredNostrEvents: $synchronizationFilter")
                database.nostrEventDao().observeFilteredNostrEvents(
                    kinds = synchronizationFilter.kinds,
                    ids = synchronizationFilter.ids
                )
            }
            synchronizationFilter.kinds != null && synchronizationFilter.authors != null -> {
                logger.d("observeAuthoredNostrEvents: $synchronizationFilter")
                database.nostrEventDao().observeAuthoredNostrEvents(
                    kinds = synchronizationFilter.kinds,
                    authors = synchronizationFilter.authors
                )
            }
            else -> {
                logger.d("observeNostrEvents: $synchronizationFilter")
                database.nostrEventDao().observeNostrEvents(
                    kinds = arrayOf(
                        TextNoteEvent.KIND,
                    )
                )
            }
        }
    }

    override suspend fun observeLocalNostrEventById(nostrEventId: String): Flow<LocalNostrEvent?> {
        return database.nostrEventDao().observeNostrEventById(nostrEventId)
    }

    override suspend fun searchableProfiles(): List<Profile> {
        return database.profileDao().getAllProfiles()
    }

    override suspend fun filterProfiles(searchTerm: String): List<LocalProfile> {
        return database.profileDao().filterProfiles(searchTerm = searchTerm)
    }


    override suspend fun observeProfileWithFollowers(publicKey: String): Flow<LocalProfileWithFollowers?> {
        return database.profileDao().observeProfileWithFollowersByPublicKey(publicKey)
    }

    override suspend fun observeProfileWithFollowing(publicKey: String): Flow<LocalProfileWithFollowing?> {
        return database.profileDao().observeProfileWithFollowingByPublicKey(publicKey)
    }

    override suspend fun getRecentSearches(): List<RecentSearch> {
        return database.recentSearchDao().getAllRecentSearches()
    }

    override suspend fun removeRecentSearch(recentSearch: RecentSearch) {
        database.recentSearchDao().deleteRecentSearch(recentSearch)
    }

    override suspend fun saveSearchQuery(
        query: String,
        synchronizationFilterArray: SynchronizationFilterArray?
    ) {
        database.recentSearchDao().upsert(
            RecentSearch(
                query = query,
                synchronizationFilters = synchronizationFilterArray
            )
        )
    }

    override suspend fun observePublicKeyRelays(publicKey: String): Flow<List<Relay>> {
        return database.relayDao().observePublicKeyRelays(publicKey)
    }
}
