package press.mantra.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import press.mantra.compose.database.model.DkgParticipantMessage
import press.mantra.compose.database.model.DkgSession
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Kind
import kotlinx.coroutines.flow.Flow

/**
 * Every read here names a profile, and that is not defensive repetition.
 *
 * `DkgSession`'s key is (`id`, `userPublicKey`) -- see the note on its entity --
 * so a query that gives only the ceremony id, or only the room, does not identify
 * a row. It would return whichever profile's the database happened to order first,
 * and what it would hand back is that profile's host key, randomness and share.
 * There is deliberately no unscoped overload to reach for.
 */
@Dao
interface DkgSessionDao {
    @Query("SELECT * FROM DkgSession WHERE id = :sessionId AND userPublicKey = :userPublicKey")
    suspend fun getSessionById(sessionId: String, userPublicKey: HexKey): DkgSession?

    @Query("SELECT * FROM DkgSession WHERE id = :sessionId AND userPublicKey = :userPublicKey")
    fun observeSessionById(sessionId: String, userPublicKey: HexKey): Flow<DkgSession?>

    /**
     * The key a ceremony produced, whichever profile is asking -- the one read here
     * that names no profile, and says so in its name.
     *
     * The scoping rule above is about rows that carry secrets. This projects a
     * single public column: the threshold key is the ceremony's published result and
     * is the same value on every participant's row by construction, so there is
     * nothing for a profile to disambiguate and nothing of anyone's to leak. What a
     * caller gets is what every member of the group already has.
     *
     * It exists because the alternative was worse in both directions. Scoping it
     * would make a screen resolve nothing while no profile is open, for a fact that
     * does not depend on one; leaving `getSessionById` unscoped would hand back a
     * whole row -- host key, randomness, share -- to answer a question about a
     * public key.
     *
     * Any row of the ceremony answers, so it takes one. A ceremony that has not
     * finished has the column null on every row and answers null, which the callers
     * want: both of them run from before it finishes.
     */
    @Query(
        "SELECT thresholdPublicKey FROM DkgSession WHERE id = :sessionId AND " +
            "thresholdPublicKey IS NOT NULL LIMIT 1"
    )
    suspend fun getCeremonyThresholdPublicKey(sessionId: String): HexKey?

    /**
     * The ritual a room is currently running, newest first. A group may have
     * abandoned earlier attempts; the live one is the most recent.
     */
    @Query(
        "SELECT * FROM DkgSession WHERE chatRoomId = :chatRoomId AND userPublicKey = :userPublicKey " +
            "ORDER BY createdAt DESC LIMIT 1"
    )
    fun observeLatestSessionForChatRoom(
        chatRoomId: String,
        userPublicKey: HexKey
    ): Flow<DkgSession?>

    @Query(
        "SELECT * FROM DkgSession WHERE chatRoomId = :chatRoomId AND userPublicKey = :userPublicKey " +
            "ORDER BY createdAt DESC LIMIT 1"
    )
    suspend fun getLatestSessionForChatRoom(
        chatRoomId: String,
        userPublicKey: HexKey
    ): DkgSession?

    /**
     * The room's newest ceremony **of its own**, newest first -- never one it is
     * only hosting on a subgroup's behalf.
     *
     * The distinction did not exist while every ceremony ran in a NIP-17 room of
     * its own. It does now: a subgroup's ChillDKG runs in the parent's Marmot
     * room, so that room holds a completed ceremony whose key is the *child's*,
     * and a caller asking "what key does this room sign with" must not be handed
     * it. That is `FrostSigningManager.completedKey`'s last fallback, which is
     * reached by every parent member who holds no key-state row -- which is
     * every member welcomed after the parent's own ceremony.
     *
     * A ceremony run to make a subgroup is never the room's own key, and
     * `parentChatRoomId` is the only thing that has to be read to know it. It is
     * an unverified claim, but not a dangerous one here: the direction it can be
     * abused in is a member *excluding* a ceremony from this answer, and a
     * ceremony they can exclude is one they proposed, whose key they would
     * simply not have proposed at all.
     *
     * Scoped to [userPublicKey] because a room's rows are not: `ChatRoom` is keyed
     * by the group id alone, so two profiles on one device that are both in a group
     * hold their ceremonies of it side by side in one table. Only the asking
     * profile's own row carries a share it can sign with.
     */
    @Query(
        "SELECT * FROM DkgSession WHERE chatRoomId = :chatRoomId AND parentChatRoomId IS NULL " +
            "AND userPublicKey = :userPublicKey ORDER BY createdAt DESC LIMIT 1"
    )
    suspend fun getLatestOwnSessionForChatRoom(
        chatRoomId: String,
        userPublicKey: HexKey
    ): DkgSession?

    /**
     * The room's newest ceremony *for one purpose*: the group's own when
     * [parentChatRoomId] is null, or the subgroup of that parent when it is not.
     *
     * A room routinely holds more than one, and since the move it is the ordinary
     * case rather than a corner: a parent's Marmot room hosts every subgroup
     * ceremony the group ever runs, alongside whatever it holds of its own.
     * `DkgSession.parentChatRoomId` is what tells those apart.
     *
     * It does **not** tell two subgroups of the same parent apart -- they share
     * both columns. Use [getLatestSubgroupSessionFor] where the question is which
     * subgroup; this answers "does this room hold a ceremony for that parent at
     * all", which is what a screen scoped to one lineage wants.
     *
     * Everything else about a ceremony is already keyed by session id -- the
     * messages, the approvals, the transcript -- so nothing below this needed to
     * learn about the second one.
     */
    @Query(
        "SELECT * FROM DkgSession WHERE chatRoomId = :chatRoomId AND " +
            "userPublicKey = :userPublicKey AND " +
            "(parentChatRoomId = :parentChatRoomId OR " +
            "(:parentChatRoomId IS NULL AND parentChatRoomId IS NULL)) " +
            "ORDER BY createdAt DESC LIMIT 1"
    )
    suspend fun getLatestSessionFor(
        chatRoomId: String,
        parentChatRoomId: String?,
        userPublicKey: HexKey
    ): DkgSession?

    @Query(
        "SELECT * FROM DkgSession WHERE chatRoomId = :chatRoomId AND " +
            "userPublicKey = :userPublicKey AND " +
            "(parentChatRoomId = :parentChatRoomId OR " +
            "(:parentChatRoomId IS NULL AND parentChatRoomId IS NULL)) " +
            "ORDER BY createdAt DESC LIMIT 1"
    )
    fun observeLatestSessionFor(
        chatRoomId: String,
        parentChatRoomId: String?,
        userPublicKey: HexKey
    ): Flow<DkgSession?>

    /**
     * The newest ceremony making a subgroup of [parentChatRoomId] over exactly
     * these admins, or null if this device knows of none.
     *
     * What "may I open another" means for a subgroup, now that the ceremony runs
     * in the parent's room. It used to be answered by the room: the ceremony room
     * was derived from the admins, so the same admins were the same room and
     * [getLatestSessionFor] folded a second ask into the first. The parent's room
     * is the same room for every subgroup it ever makes, so that scoping would
     * refuse the parent's *second* subgroup on the strength of its first.
     *
     * [participantPublicKeys] is the same sorted string
     * `DkgSession.formatParticipants` writes, which is why it is sorted: the
     * coordinator builds the set from a picker and every other device from
     * p-tags, and the two have to match as strings for this to find anything.
     *
     * Deliberately not scoped by stage -- the caller decides what a *finished*
     * ceremony means. `ChillDkgRitualManager.proposeRitual` folds into one that
     * is still running and opens a new one otherwise, because a subgroup that
     * has been made is not a reason to refuse making another over the same
     * people.
     */
    @Query(
        "SELECT * FROM DkgSession WHERE chatRoomId = :chatRoomId AND " +
            "parentChatRoomId = :parentChatRoomId AND " +
            "participantPublicKeys = :participantPublicKeys AND " +
            "userPublicKey = :userPublicKey " +
            "ORDER BY createdAt DESC LIMIT 1"
    )
    suspend fun getLatestSubgroupSessionFor(
        chatRoomId: String,
        parentChatRoomId: String,
        participantPublicKeys: String,
        userPublicKey: HexKey
    ): DkgSession?

    /**
     * Every ceremony in [chatRoomId] opened for one particular thing, newest
     * first.
     *
     * Both halves of the origin are matched, because a row holding one of them is
     * a row written by a version that disagreed with this one -- see
     * `DkgSession.origin`.
     *
     * The stage is deliberately not in the `WHERE`. `DkgRitualStage` is stored
     * through a converter, so a SQL comparison against it would be this query
     * deciding what the converter's output looks like; the caller filters in
     * Kotlin, where the enum is the enum.
     */
    @Query(
        "SELECT * FROM DkgSession WHERE chatRoomId = :chatRoomId AND " +
            "originKind = :originKind AND originId = :originId " +
            "ORDER BY createdAt DESC"
    )
    suspend fun getSessionsForOrigin(
        chatRoomId: String,
        originKind: Int,
        originId: String
    ): List<DkgSession>

    /**
     * Every ceremony this room holds, newest first.
     *
     * A room holds more than one the moment a subgroup's admins are the whole
     * group, so the standing "this needs you" notice in the transcript cannot ask
     * about "the room's ceremony" -- it has to look at all of them and say which.
     */
    @Query(
        "SELECT * FROM DkgSession WHERE chatRoomId = :chatRoomId AND userPublicKey = :userPublicKey " +
            "ORDER BY createdAt DESC"
    )
    fun observeSessionsForChatRoom(
        chatRoomId: String,
        userPublicKey: HexKey
    ): Flow<List<DkgSession>>

    /**
     * Every ceremony [userPublicKey] came out of holding a share, newest first.
     *
     * Signing happens in the admin room, whose id is derived from the key
     * rather than from the room the ceremony ran in, so the key is found by
     * matching that derivation rather than by a stored room id.
     *
     * One profile's, not the device's. A device holds as many of these as it has
     * profiles, and a share is only usable by the profile whose ceremony produced
     * it: the secret here reconstructs *that* participant's share, and the signer
     * id a session would use is that participant's index. Handing one profile the
     * other's ceremony answers "this group can sign" to somebody holding nothing
     * to sign with, which the UI reads as permission to offer the action.
     */
    @Query(
        "SELECT * FROM DkgSession WHERE thresholdPublicKey IS NOT NULL AND secretShare IS NOT NULL " +
            "AND userPublicKey = :userPublicKey ORDER BY createdAt DESC"
    )
    suspend fun getKeyHoldingSessions(userPublicKey: HexKey): List<DkgSession>

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
    @Query(
        "SELECT * FROM DkgSession WHERE parentChatRoomId = :parentChatRoomId AND " +
            "userPublicKey = :userPublicKey ORDER BY createdAt DESC"
    )
    suspend fun getByParentChatRoomId(
        parentChatRoomId: String,
        userPublicKey: HexKey
    ): List<DkgSession>

    @Upsert
    suspend fun upsert(dkgSession: DkgSession)

    @Upsert
    suspend fun upsert(dkgParticipantMessage: DkgParticipantMessage)

    /**
     * The messages [userPublicKey] has received of one round, in participant order.
     *
     * Ordered by sender, which is what makes it usable: the ritual derives each
     * participant's FROST id from the bytewise sort of the host keys, so a caller
     * reading this back gets the same order every device did.
     */
    @Query(
        "SELECT * FROM DkgParticipantMessage WHERE sessionId = :sessionId AND " +
            "userPublicKey = :userPublicKey AND kind = :kind ORDER BY participantPublicKey ASC"
    )
    suspend fun getMessagesByKind(
        sessionId: String,
        userPublicKey: HexKey,
        kind: Kind
    ): List<DkgParticipantMessage>

    @Query(
        "SELECT * FROM DkgParticipantMessage WHERE sessionId = :sessionId AND " +
            "userPublicKey = :userPublicKey ORDER BY createdAt ASC"
    )
    fun observeMessages(
        sessionId: String,
        userPublicKey: HexKey
    ): Flow<List<DkgParticipantMessage>>

    /**
     * How many of a round's messages [userPublicKey] holds -- which is how the
     * ritual knows whether a step can run.
     *
     * The count is of one profile's rows on purpose. Counting the device's would
     * reach the threshold while this profile had heard from nobody.
     */
    @Query(
        "SELECT COUNT(*) FROM DkgParticipantMessage WHERE sessionId = :sessionId AND " +
            "userPublicKey = :userPublicKey AND kind = :kind"
    )
    suspend fun countMessagesByKind(
        sessionId: String,
        userPublicKey: HexKey,
        kind: Kind
    ): Int

    @Query(
        "SELECT * FROM DkgParticipantMessage WHERE sessionId = :sessionId AND " +
            "userPublicKey = :userPublicKey AND kind = :kind AND " +
            "participantPublicKey = :participantPublicKey"
    )
    suspend fun getMessage(
        sessionId: String,
        userPublicKey: HexKey,
        kind: Kind,
        participantPublicKey: HexKey
    ): DkgParticipantMessage?
}
