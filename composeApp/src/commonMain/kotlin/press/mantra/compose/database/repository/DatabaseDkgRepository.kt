package press.mantra.compose.database.repository

import press.mantra.compose.database.MantraDatabase
import press.mantra.compose.database.model.DkgParticipantMessage
import press.mantra.compose.database.model.DkgSession
import press.mantra.compose.database.model.FrostSigningSession
import press.mantra.compose.database.model.GroupKeyState
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.database.model.intermdiate.LocalFrostSigningSession
import press.mantra.compose.database.model.types.DkgApprovalStep
import press.mantra.compose.managers.ChillDkgRitualManager
import press.mantra.compose.managers.GroupKeyStateManager
import press.mantra.compose.managers.SharedKeyDerivation
import press.mantra.compose.nostr.frost.GroupKeyStateEvent
import press.mantra.compose.repository.DkgRepository
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DatabaseDkgRepository(
    private val database: MantraDatabase,
    private val scope: CoroutineScope
): DkgRepository {
    private val logger = Logger.withTag(TAG)

    override fun observeLatestSessionForChatRoom(chatRoomId: String): Flow<DkgSession?> =
        database.dkgSessionDao().observeLatestSessionForChatRoom(chatRoomId)

    override fun observeMessages(sessionId: String): Flow<List<DkgParticipantMessage>> =
        database.dkgSessionDao().observeMessages(sessionId)

    override suspend fun getLatestSessionForChatRoom(chatRoomId: String): DkgSession? =
        database.dkgSessionDao().getLatestSessionForChatRoom(chatRoomId)

    override suspend fun proposeRitual(
        localChatRoom: LocalChatRoom,
        userPublicKey: HexKey,
        nostrPrivateKey: ByteArray,
        threshold: Int
    ): DkgSession? = try {
        ChillDkgRitualManager.proposeRitual(
            database = database,
            localChatRoom = localChatRoom,
            userPublicKey = userPublicKey,
            nostrPrivateKey = nostrPrivateKey,
            threshold = threshold
        )
    } catch (e: Throwable) {
        logger.e("Error proposing DKG ritual for ${localChatRoom.chatRoom.id}", e)
        null
    }

    override suspend fun pendingApproval(session: DkgSession): DkgApprovalStep? =
        ChillDkgRitualManager.pendingApproval(database, session)

    override suspend fun proposeGroupKeyState(
        localChatRoom: LocalChatRoom,
        userPublicKey: HexKey,
        session: DkgSession
    ): FrostSigningSession? = try {
        GroupKeyStateManager.propose(
            database = database,
            localChatRoom = localChatRoom,
            userPublicKey = userPublicKey,
            key = session
        )
    } catch (e: Throwable) {
        // A room with no standing to propose is a bug rather than a condition,
        // and a ceremony with no key yet is a caller that got ahead of itself.
        // Neither is worth failing the room over: the group still has a working
        // chat, and the screen reports a proposal that did not open by leaving
        // the group with nothing signed and the button still there.
        logger.e("Error proposing the key state from ${localChatRoom.chatRoom.id}", e)
        null
    }

    override fun observeSigningSessions(
        chatRoomId: String
    ): Flow<List<LocalFrostSigningSession>> =
        database.frostSigningSessionDao().observeSessionsForChatRoom(chatRoomId)

    override fun observeSignedGroupKeyState(chatRoomId: String): Flow<GroupKeyState?> =
        database.groupSignedEventDao()
            .observeByKind(GroupKeyStateEvent.KIND)
            .map { signedEvents ->
                // The room the ceremony's key derives, resolved per emission
                // rather than once: the ceremony has no key until it finishes,
                // and this flow is running before it does.
                val adminRoomId = database.dkgSessionDao()
                    .getLatestSessionForChatRoom(chatRoomId)
                    ?.thresholdPublicKey
                    ?.let { runCatching { SharedKeyDerivation.marmotGroupId(it) }.getOrNull() }

                adminRoomId?.let { GroupKeyStateManager.stateAmong(signedEvents, it) }
            }

    override suspend fun adoptGroupKeyState(chatRoomId: String): GroupKeyState? = try {
        GroupKeyStateManager.adopt(database = database, chatRoomId = chatRoomId)
    } catch (e: Throwable) {
        // The room is made and usable either way; what it loses is the shortcut
        // from room to ceremony, and `FrostSigningManager.completedKey` still
        // rederives its way to the same key.
        logger.e("Error adopting the signed key state for $chatRoomId", e)
        null
    }

    override suspend fun approve(
        localChatRoom: LocalChatRoom,
        sessionId: String,
        step: DkgApprovalStep,
        nostrPrivateKey: ByteArray
    ) {
        try {
            ChillDkgRitualManager.approve(
                database = database,
                localChatRoom = localChatRoom,
                sessionId = sessionId,
                step = step,
                nostrPrivateKey = nostrPrivateKey
            )
        } catch (e: Throwable) {
            // The ritual fails itself and tells the group; swallowing here keeps a
            // protocol fault from taking the screen down with it.
            logger.e("Error approving $step for DKG ritual $sessionId", e)
        }
    }

    companion object {
        private const val TAG = "DatabaseDkgRepository"
    }
}
