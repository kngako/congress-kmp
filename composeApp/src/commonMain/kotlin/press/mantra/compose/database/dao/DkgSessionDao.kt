package press.mantra.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import press.mantra.compose.database.model.DkgParticipantMessage
import press.mantra.compose.database.model.DkgSession
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Kind
import kotlinx.coroutines.flow.Flow

@Dao
interface DkgSessionDao {
    @Query("SELECT * FROM DkgSession WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: String): DkgSession?

    @Query("SELECT * FROM DkgSession WHERE id = :sessionId")
    fun observeSessionById(sessionId: String): Flow<DkgSession?>

    /**
     * The ritual a room is currently running, newest first. A group may have
     * abandoned earlier attempts; the live one is the most recent.
     */
    @Query("SELECT * FROM DkgSession WHERE chatRoomId = :chatRoomId ORDER BY createdAt DESC LIMIT 1")
    fun observeLatestSessionForChatRoom(chatRoomId: String): Flow<DkgSession?>

    @Query("SELECT * FROM DkgSession WHERE chatRoomId = :chatRoomId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestSessionForChatRoom(chatRoomId: String): DkgSession?

    /**
     * The room's newest ceremony *for one purpose*: the group's own when
     * [parentChatRoomId] is null, or the subgroup of that parent when it is not.
     *
     * A room can hold more than one. The ceremony room for a set of admins is
     * derived from those admins, so a subgroup whose admins are everybody lands
     * in the room the group's own ceremony was held in -- and that is a legitimate
     * subgroup rather than a mistake, because a subgroup is a logical division and
     * not a smaller membership.
     *
     * `DkgSession.parentChatRoomId` is what tells them apart, and it is enough
     * because it is the only thing that differs: two ceremonies in one room are
     * either one group's and one subgroup's, or two subgroups' of different
     * parents. Two subgroups of the *same* parent over the *same* admins would
     * still collide, and that pair is one subgroup asked for twice.
     *
     * Everything else about a ceremony is already keyed by session id -- the
     * messages, the approvals, the transcript -- so nothing below this needed to
     * learn about the second one.
     */
    @Query(
        "SELECT * FROM DkgSession WHERE chatRoomId = :chatRoomId AND " +
            "(parentChatRoomId = :parentChatRoomId OR " +
            "(:parentChatRoomId IS NULL AND parentChatRoomId IS NULL)) " +
            "ORDER BY createdAt DESC LIMIT 1"
    )
    suspend fun getLatestSessionFor(chatRoomId: String, parentChatRoomId: String?): DkgSession?

    @Query(
        "SELECT * FROM DkgSession WHERE chatRoomId = :chatRoomId AND " +
            "(parentChatRoomId = :parentChatRoomId OR " +
            "(:parentChatRoomId IS NULL AND parentChatRoomId IS NULL)) " +
            "ORDER BY createdAt DESC LIMIT 1"
    )
    fun observeLatestSessionFor(chatRoomId: String, parentChatRoomId: String?): Flow<DkgSession?>

    /**
     * Every ceremony this room holds, newest first.
     *
     * A room holds more than one the moment a subgroup's admins are the whole
     * group, so the standing "this needs you" notice in the transcript cannot ask
     * about "the room's ceremony" -- it has to look at all of them and say which.
     */
    @Query("SELECT * FROM DkgSession WHERE chatRoomId = :chatRoomId ORDER BY createdAt DESC")
    fun observeSessionsForChatRoom(chatRoomId: String): Flow<List<DkgSession>>

    /**
     * Every ceremony this device came out of holding a share, newest first.
     *
     * Signing happens in the #admins room, whose id is derived from the key
     * rather than from the room the ceremony ran in, so the key is found by
     * matching that derivation rather than by a stored room id.
     */
    @Query("SELECT * FROM DkgSession WHERE thresholdPublicKey IS NOT NULL AND secretShare IS NOT NULL ORDER BY createdAt DESC")
    suspend fun getKeyHoldingSessions(): List<DkgSession>

    /**
     * Ceremonies opened to make a subgroup of [parentChatRoomId], newest first.
     *
     * How a member gets back into a subgroup flow they closed the app during:
     * the child's room does not exist yet and its certificate may not be signed,
     * so the ceremony is the only thing on this device that knows the flow was
     * ever started.
     *
     * The parent named here is an unverified claim off the proposal -- see
     * `DkgSession.parentChatRoomId` -- so nothing may be granted on the strength
     * of appearing in this list. It decides which screen to offer, not who may
     * do what.
     */
    @Query("SELECT * FROM DkgSession WHERE parentChatRoomId = :parentChatRoomId ORDER BY createdAt DESC")
    suspend fun getByParentChatRoomId(parentChatRoomId: String): List<DkgSession>

    @Upsert
    suspend fun upsert(dkgSession: DkgSession)

    @Upsert
    suspend fun upsert(dkgParticipantMessage: DkgParticipantMessage)

    @Query("SELECT * FROM DkgParticipantMessage WHERE sessionId = :sessionId AND kind = :kind ORDER BY participantPublicKey ASC")
    suspend fun getMessagesByKind(sessionId: String, kind: Kind): List<DkgParticipantMessage>

    @Query("SELECT * FROM DkgParticipantMessage WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    fun observeMessages(sessionId: String): Flow<List<DkgParticipantMessage>>

    @Query("SELECT COUNT(*) FROM DkgParticipantMessage WHERE sessionId = :sessionId AND kind = :kind")
    suspend fun countMessagesByKind(sessionId: String, kind: Kind): Int

    @Query("SELECT * FROM DkgParticipantMessage WHERE sessionId = :sessionId AND kind = :kind AND participantPublicKey = :participantPublicKey")
    suspend fun getMessage(sessionId: String, kind: Kind, participantPublicKey: HexKey): DkgParticipantMessage?
}
