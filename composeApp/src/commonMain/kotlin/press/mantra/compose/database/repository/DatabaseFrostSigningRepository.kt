package press.mantra.compose.database.repository

import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Kind
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import press.mantra.compose.database.MantraDatabase
import press.mantra.compose.database.model.FrostSignerMessage
import press.mantra.compose.database.model.FrostSigningItem
import press.mantra.compose.database.model.FrostSigningSession
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.database.model.intermdiate.LocalFrostSigningSession
import press.mantra.compose.identity.Identity
import press.mantra.compose.managers.FrostSigningManager
import press.mantra.compose.repository.FrostSigningRepository

/**
 * @param activeIdentity whose shares [canSign] answers about. A device holds as many
 *   profiles as the user signed in, and a group one of them holds a share of is a
 *   group the next is only reading; the question is therefore per-profile and the
 *   answer is the repository's to scope, not the call site's -- a screen asking "can
 *   this group sign" means itself, and cannot be made to mean anyone else. Defaults
 *   to nobody, which can sign nothing, and is what a test with no identity gets.
 */
class DatabaseFrostSigningRepository(
    private val database: MantraDatabase,
    private val scope: CoroutineScope,
    private val activeIdentity: StateFlow<Identity?> = MutableStateFlow(null),
): FrostSigningRepository {
    private val logger = Logger.withTag(TAG)

    override fun observeSessionById(sessionId: String): Flow<FrostSigningSession?> =
        database.frostSigningSessionDao().observeSessionById(sessionId)

    override fun observeSessionsForChatRoom(chatRoomId: String): Flow<List<LocalFrostSigningSession>> =
        database.frostSigningSessionDao().observeSessionsForChatRoom(chatRoomId)

    override fun observeMessages(sessionId: String): Flow<List<FrostSignerMessage>> =
        database.frostSigningSessionDao().observeMessages(sessionId)

    override fun observeItems(sessionId: String): Flow<List<FrostSigningItem>> =
        database.frostSigningSessionDao().observeItems(sessionId)

    override suspend fun getItems(sessionId: String): List<FrostSigningItem> =
        database.frostSigningSessionDao().getItems(sessionId)

    override suspend fun getSessionById(sessionId: String): FrostSigningSession? =
        database.frostSigningSessionDao().getSessionById(sessionId)

    /**
     * Whether the active profile can sign for this room.
     *
     * Nobody active signs nothing. A read-only profile reaches here too and is
     * refused on the share rather than on the private key -- it holds no ceremony,
     * so there is nothing to find.
     */
    override suspend fun canSign(chatRoomId: String): Boolean {
        val userPublicKey = activeIdentity.value?.nostrPublicKey ?: return false

        return FrostSigningManager.canSign(database, chatRoomId, userPublicKey)
    }

    override suspend fun proposeSigning(
        localChatRoom: LocalChatRoom,
        userPublicKey: HexKey,
        kind: Kind,
        tags: Array<Array<String>>,
        content: String
    ): FrostSigningSession? = proposing(localChatRoom) {
        FrostSigningManager.proposeSigning(
            database = database,
            localChatRoom = localChatRoom,
            userPublicKey = userPublicKey,
            kind = kind,
            tags = tags,
            content = content
        )
    }

    override suspend fun proposeSigningBatch(
        localChatRoom: LocalChatRoom,
        userPublicKey: HexKey,
        events: List<EventTemplate<*>>
    ): FrostSigningSession? = proposing(localChatRoom) {
        FrostSigningManager.proposeSigningBatch(
            database = database,
            localChatRoom = localChatRoom,
            userPublicKey = userPublicKey,
            events = events
        )
    }

    override suspend fun proposeSigningBatch(
        localChatRoom: LocalChatRoom,
        userPublicKey: HexKey,
        lead: EventTemplate<*>,
        dependents: (lead: Event) -> List<EventTemplate<*>>
    ): FrostSigningSession? = proposing(localChatRoom) {
        FrostSigningManager.proposeSigningBatch(
            database = database,
            localChatRoom = localChatRoom,
            userPublicKey = userPublicKey,
            lead = lead,
            dependents = dependents
        )
    }

    /**
     * Proposing throws when the group has no key, when this device was not in the
     * ceremony, or when a batch is empty or over the cap. All four are states the
     * UI is supposed to have checked for, so they become a null the caller
     * reports rather than a crash.
     */
    private inline fun proposing(
        localChatRoom: LocalChatRoom,
        propose: () -> FrostSigningSession
    ): FrostSigningSession? = try {
        propose()
    } catch (e: Throwable) {
        logger.e("Error proposing a signature in ${localChatRoom.chatRoom.id}", e)
        null
    }

    override suspend fun approve(localChatRoom: LocalChatRoom, sessionId: String) {
        try {
            FrostSigningManager.approve(
                database = database,
                localChatRoom = localChatRoom,
                sessionId = sessionId
            )
        } catch (e: Throwable) {
            // The session fails itself and tells the group; swallowing here keeps a
            // protocol fault from taking the screen down with it.
            logger.e("Error approving signing session $sessionId", e)
        }
    }

    override suspend fun decline(localChatRoom: LocalChatRoom, sessionId: String) {
        try {
            FrostSigningManager.decline(
                database = database,
                localChatRoom = localChatRoom,
                sessionId = sessionId
            )
        } catch (e: Throwable) {
            logger.e("Error declining signing session $sessionId", e)
        }
    }

    override fun unsignedEvents(items: List<FrostSigningItem>): List<Event> =
        items.map { Event.fromJson(it.unsignedEventJson) }

    override fun signedEvents(items: List<FrostSigningItem>): List<Event> =
        FrostSigningManager.signedEvents(items)

    companion object {
        private const val TAG = "DatabaseFrostSigningRepository"
    }
}
