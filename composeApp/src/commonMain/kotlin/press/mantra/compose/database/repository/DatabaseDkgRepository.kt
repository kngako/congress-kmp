package press.mantra.compose.database.repository

import press.mantra.compose.database.MantraDatabase
import press.mantra.compose.database.model.DkgParticipantMessage
import press.mantra.compose.database.model.DkgSession
import press.mantra.compose.database.model.types.DkgRitualStage
import press.mantra.compose.database.model.FrostSigningSession
import press.mantra.compose.database.model.intermdiate.LocalGroupKeyState
import press.mantra.compose.database.model.GroupSignedEvent
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.database.model.intermdiate.LocalFrostSigningSession
import press.mantra.compose.database.model.types.DkgApprovalStep
import press.mantra.compose.managers.ChillDkgRitualManager
import press.mantra.compose.managers.GroupKeyStateManager
import press.mantra.compose.managers.SubgroupManager
import press.mantra.compose.managers.SubgroupOrigin
import press.mantra.compose.managers.SharedKeyDerivation
import press.mantra.compose.nostr.frost.GroupKeyStateEvent
import press.mantra.compose.nostr.subgroup.SubgroupBirthCertificateEvent
import press.mantra.compose.repository.DkgRepository
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import press.mantra.compose.identity.Identity

/**
 * @param activeIdentity whose rituals this answers about. A `DkgSession` is keyed by
 *   the ceremony *and* the profile that ran it, so none of the reads below name a row
 *   without one -- and the profile is the repository's to supply rather than the
 *   screen's, which asks about the ritual it is showing and can mean no one else.
 *   Defaults to nobody, which has no rituals, and is what a test with no identity
 *   gets.
 */
class DatabaseDkgRepository(
    private val database: MantraDatabase,
    private val scope: CoroutineScope,
    private val activeIdentity: StateFlow<Identity?> = MutableStateFlow(null),
): DkgRepository {
    private val logger = Logger.withTag(TAG)

    /** The profile asking, or null when nothing is open. */
    private val userPublicKey: HexKey? get() = activeIdentity.value?.nostrPublicKey

    override fun observeSessionById(sessionId: String): Flow<DkgSession?> =
        userPublicKey
            ?.let { database.dkgSessionDao().observeSessionById(sessionId, it) }
            ?: flowOf(null)

    override fun observeSessionsForChatRoom(chatRoomId: String): Flow<List<DkgSession>> =
        userPublicKey
            ?.let { database.dkgSessionDao().observeSessionsForChatRoom(chatRoomId, it) }
            ?: flowOf(emptyList())

    override suspend fun roomAwaitingCreation(session: DkgSession): String? {
        if (session.stage != DkgRitualStage.COMPLETE) return null

        val roomId = session.thresholdPublicKey
            ?.let { runCatching { SharedKeyDerivation.marmotGroupId(it) }.getOrNull() }
            ?: return null

        return roomId.takeIf { database.chatRoomDao().findChatRoomById(it) == null }
    }

    override fun observeLatestSessionForChatRoom(
        chatRoomId: String,
        parentChatRoomId: String?,
    ): Flow<DkgSession?> =
        userPublicKey
            ?.let { database.dkgSessionDao().observeLatestSessionFor(chatRoomId, parentChatRoomId, it) }
            ?: flowOf(null)

    override fun observeMessages(sessionId: String): Flow<List<DkgParticipantMessage>> =
        userPublicKey
            ?.let { database.dkgSessionDao().observeMessages(sessionId, it) }
            ?: flowOf(emptyList())

    override suspend fun getLatestSessionForChatRoom(chatRoomId: String): DkgSession? =
        userPublicKey?.let { database.dkgSessionDao().getLatestSessionForChatRoom(chatRoomId, it) }

    override suspend fun proposeRitual(
        localChatRoom: LocalChatRoom,
        userPublicKey: HexKey,
        nostrPrivateKey: ByteArray,
        threshold: Int,
        parentChatRoomId: HexKey?,
        participantPublicKeys: Set<HexKey>?,
        subject: String?,
        origin: SubgroupOrigin.Ref?
    ): DkgSession? = try {
        ChillDkgRitualManager.proposeRitual(
            database = database,
            localChatRoom = localChatRoom,
            userPublicKey = userPublicKey,
            nostrPrivateKey = nostrPrivateKey,
            threshold = threshold,
            parentChatRoomId = parentChatRoomId,
            participantPublicKeys = participantPublicKeys,
            subject = subject,
            origin = origin
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

    /**
     * By session id, not by room. A ceremony room holds more than one ceremony
     * the moment a subgroup's admins are the whole group, and a lookup by room
     * answered about whichever the room's rule picked -- for a room holding only
     * a subgroup's ceremony, asking for the room's *own* found nothing at all,
     * and a finished subgroup with a signed key state could never be created.
     */
    /**
     * Off the ceremony's own key rather than a profile's row.
     *
     * The threshold key is the ceremony's published result and the same value on every
     * participant's row, so this is the one read here that names no profile -- see
     * `DkgSessionDao.getCeremonyThresholdPublicKey`. Scoping it would make the two flows
     * below resolve nothing while no profile is open, for a fact that does not depend on
     * one.
     */
    override suspend fun roomDerivedBy(dkgSessionId: String): String? =
        database.dkgSessionDao()
            .getCeremonyThresholdPublicKey(dkgSessionId)
            ?.let { runCatching { SharedKeyDerivation.marmotGroupId(it) }.getOrNull() }

    override fun observeSignedGroupKeyState(dkgSessionId: String): Flow<LocalGroupKeyState?> =
        database.groupSignedEventDao()
            .observeByKind(GroupKeyStateEvent.KIND)
            .map { signedEvents ->
                // Resolved per emission rather than once: the ceremony has no key
                // until it finishes, and this flow is running before it does.
                roomDerivedBy(dkgSessionId)
                    ?.let { GroupKeyStateManager.stateAmong(signedEvents, it) }
            }

    override suspend fun adoptGroupKeyState(chatRoomId: String): LocalGroupKeyState? = try {
        GroupKeyStateManager.adopt(database = database, chatRoomId = chatRoomId)
    } catch (e: Throwable) {
        // The room is made and usable either way; what it loses is the shortcut
        // from room to ceremony, and `FrostSigningManager.completedKey` still
        // rederives its way to the same key.
        logger.e("Error adopting the signed key state for $chatRoomId", e)
        null
    }

    override suspend fun proposeBirthCertificate(
        parentChatRoomId: String,
        userPublicKey: HexKey,
        session: DkgSession,
        adminPublicKeys: List<HexKey>,
        name: String,
    ): FrostSigningSession? = try {
        val parentRoom = database.chatRoomDao().findChatRoomById(parentChatRoomId)
            ?: throw IllegalStateException("No parent room $parentChatRoomId to certify from")

        SubgroupManager.proposeBirthCertificate(
            database = database,
            parentRoom = parentRoom,
            userPublicKey = userPublicKey,
            key = session,
            adminPublicKeys = adminPublicKeys,
            name = name,
        )
    } catch (e: Throwable) {
        // A device with no share of the parent's key cannot open this session,
        // and a ceremony with no key yet is a caller that got ahead of itself.
        // Neither is worth failing anything over: nothing was published, and the
        // screen reports it by leaving the rung unfinished with the button there.
        logger.e("Error proposing a birth certificate in $parentChatRoomId", e)
        null
    }

    override fun observeBirthCertificate(
        dkgSessionId: String,
        parentChatRoomId: String,
    ): Flow<GroupSignedEvent?> =
        database.groupSignedEventDao()
            .observeByKind(SubgroupBirthCertificateEvent.KIND)
            .map { signedEvents ->
                // Resolved per emission rather than once: the ceremony has no key
                // until it finishes, and this flow is running before it does.
                val subgroupChatRoomId = roomDerivedBy(dkgSessionId) ?: return@map null

                signedEvents
                    .filter {
                        SubgroupBirthCertificateEvent.certifies(
                            event = it.toEvent(),
                            subgroupChatRoomId = subgroupChatRoomId,
                            parentChatRoomId = parentChatRoomId,
                        )
                    }
                    .maxByOrNull { it.createdAt }
            }

    override suspend fun proposeSubgroupKeyState(
        ceremonyRoom: LocalChatRoom,
        userPublicKey: HexKey,
        session: DkgSession,
        parentChatRoomId: HexKey,
    ): FrostSigningSession? = try {
        SubgroupManager.proposeSubgroupKeyState(
            database = database,
            ceremonyRoom = ceremonyRoom,
            userPublicKey = userPublicKey,
            key = session,
            parentChatRoomId = parentChatRoomId,
        )
    } catch (e: Throwable) {
        logger.e("Error proposing the subgroup key state from ${ceremonyRoom.chatRoom.id}", e)
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
                // Whose approval it is. Nobody open approves nothing: the manager
                // reads the row for this profile, and there is no row for null.
                userPublicKey = userPublicKey ?: return,
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
