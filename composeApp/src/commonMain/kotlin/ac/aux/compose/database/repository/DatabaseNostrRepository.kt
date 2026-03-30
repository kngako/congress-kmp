package ac.aux.compose.database.repository

import ac.aux.compose.database.AuxDatabase
import ac.aux.compose.database.model.BroadcastNostrEventReceipt
import ac.aux.compose.database.model.BroadcastNostrEventRequest
import ac.aux.compose.database.model.NostrEvent
import ac.aux.compose.database.model.Profile
import ac.aux.compose.database.model.SynchronizeNostrEventRequest
import ac.aux.compose.database.model.UnsignedNostrEvent
import ac.aux.compose.database.model.intermdiate.LocalBroadcastNostrEventRequest
import ac.aux.compose.database.model.intermdiate.LocalProfile
import ac.aux.compose.repository.NostrRepository
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock
import kotlin.time.Instant

class DatabaseNostrRepository(
    private val database: AuxDatabase
): NostrRepository {
    companion object {
        const val TAG = "DatabaseNostrRepository"
    }

    val logger = Logger.withTag(TAG)

    override suspend fun observeProfile(publicKey: HexKey): Flow<LocalProfile?> {
        return database.unsignedNostrEventDao().observeProfile(publicKey)
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
            Instant.fromEpochMilliseconds(1231006505000L)
        } else {
            null
        }

        val unsignedNostrEvent = saveUnsignedNostrEvent(
            UnsignedNostrEvent(
                pubKey = publicKey,
                kind = profileEventTemplate.kind,
                createdAt = Instant.fromEpochMilliseconds(profileEventTemplate.createdAt),
                tags = profileEventTemplate.tags,
                content = profileEventTemplate.content,
                signedAt = signedAt // This will keep us from trying to sign this event... but we should automatically sync the npub profile
            )
        )

        if (unsignedNostrEvent == null) {
            onError.invoke()
        } else if (unsignedNostrEvent.kind == 0 ) {
            // Look busy for the onboarding screen
            delay(3_500)

//            if (unsignedNostrEvent.signedAt != null) {
//                // Submit a SyncNostrEventRequest
//                synchronizationRelayURLs.forEach { relayUrl ->
//                    database.synchronizeNostrEventRequestDao().upsert(
//                        SynchronizeNostrEventRequest(
//                            authorPublicKeys = arrayOf(unsignedNostrEvent.pubKey),
//                            kinds = arrayOf(0),
//                            status = "pending",
//                            relayURL = relayUrl
//                        )
//                    )
//                }
//            }
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
                createdAt = Instant.fromEpochMilliseconds(textNote.createdAt),
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

    override suspend fun broadcastProcessed(broadcastNostrEventRequest: BroadcastNostrEventRequest) {
        logger.d("Update local reference: $broadcastNostrEventRequest")
        database.broadcastNostrEventRequestDao().upsert(
            broadcastNostrEventRequest.copy(
                status = "sent"
            )
        )
        logger.d("Processed: $broadcastNostrEventRequest")
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
            synchronizationRelayURLs = synchronizationRelayURLs
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

    override suspend fun loadNostrFeed(): List<NostrEvent> {
        return database.nostrEventDao().getAllNostrEvents()
    }

}