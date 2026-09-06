package press.mantra.compose.managers

import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlin.time.Clock
import kotlin.time.Instant
import press.mantra.compose.database.MantraDatabase
import press.mantra.compose.database.model.DkgSession
import press.mantra.compose.database.model.FrostSigningSession
import press.mantra.compose.database.model.GroupKeyState
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.nostr.frost.GroupKeyStateEvent

/**
 * Puts what key a room signs with to the group, and files what the group says.
 *
 * The room's creator [propose]s it as the new room's first message; every
 * device [record]s the state once the signing session behind it produces a
 * signature. Both ends land on the same [GroupKeyState] row, which is what a
 * signing request is resolved against -- see `FrostSigningManager.completedKey`.
 *
 * ### Why this is proposed rather than announced
 *
 * It used to be announced: the coordinator wrote the row, said so in the room,
 * and every receiver kept the statement if the room's id rederived from the key
 * it named. That check is still here and still the thing safety rests on -- a
 * state that does not rederive its own room is dropped, whoever it came from --
 * but it left the *first* thing a group ever does as the one thing one member
 * decides alone.
 *
 * So it goes through the same door everything else the group says goes through.
 * A room's key state is now a `FrostSigningEvents.PROPOSAL` over a
 * [GroupKeyStateEvent], and the state exists when a quorum has signed it, on
 * every device at once, authored by the group's own key. The first thing the
 * group does is now something the group did.
 *
 * Nothing about that makes the room usable any later than before: signing falls
 * back to rederiving while the session runs, which is exactly what every room
 * did before this table existed.
 */
object GroupKeyStateManager {
    private const val TAG = "GroupKeyStateManager"

    private val logger = Logger.withTag(TAG)

    /** The key state a room signs under, or null while it has none. */
    suspend fun keyStateFor(database: MantraDatabase, chatRoomId: String): GroupKeyState? =
        database.groupKeyStateDao().getByChatRoomId(chatRoomId)

    /**
     * Asks the group to say what the freshly made room signs with.
     *
     * Called once, by the member who created the room, as its first application
     * message -- so a member arriving on a welcome finds the session waiting
     * rather than having to be told about the key separately.
     *
     * No row is written here, and that is the whole change. The state is not
     * this device's to assert; it appears on every device together when
     * `FrostSigningManager` completes the session and applies the signed event,
     * which is the same path that turns a signed proposal into a dialect.
     *
     * The session signs at the room's own path, so what comes back is a state
     * signed by the very key it names -- the room's id being that key. A reader
     * needs nothing but the event and the room it arrived in to check that.
     *
     * Refuses to propose a state that does not describe the room, because a
     * state that fails [GroupKeyState.verifies] here is this device having
     * derived the room from one key and proposed another -- a bug worth failing
     * on rather than asking the group to sign.
     *
     * [key] is handed to the signing session rather than looked up from the
     * room, because the room has no key state yet and looking one up is exactly
     * what this session exists to make possible.
     *
     * A session of one, always, and never batched with anything else. A batch is
     * all-or-nothing, so it is only as available as its worst item -- and this is
     * the statement every other session in the room is opened against. Bundling
     * it with a dialect would make the room's ability to sign at all depend on
     * that dialect's aggregation succeeding.
     */
    suspend fun propose(
        database: MantraDatabase,
        localChatRoom: LocalChatRoom,
        userPublicKey: HexKey,
        key: DkgSession,
        path: List<Long> = SharedKeyDerivation.MARMOT_ADMIN_GROUP_PATH,
        createdAt: Long = Clock.System.now().epochSeconds
    ): FrostSigningSession {
        val chatRoomId = localChatRoom.chatRoom.id
        val thresholdPublicKey = key.thresholdPublicKey
            ?: throw IllegalStateException("Ceremony ${key.id} has produced no key for $chatRoomId to sign with")

        val state = GroupKeyState(
            chatRoomId = chatRoomId,
            dkgSessionId = key.id,
            thresholdPublicKey = thresholdPublicKey,
            derivationPath = SharedKeyDerivation.formatPath(path),
            announcedBy = userPublicKey,
            announcedAt = Instant.fromEpochSeconds(createdAt)
        )

        check(state.verifies()) {
            "Room $chatRoomId is not derived from $thresholdPublicKey at ${state.derivationPath}"
        }

        logger.i("Proposing key ${state.thresholdPublicKey} for room $chatRoomId at ${state.derivationPath}")

        return FrostSigningManager.proposeSigning(
            database = database,
            localChatRoom = localChatRoom,
            userPublicKey = userPublicKey,
            kind = GroupKeyStateEvent.KIND,
            tags = GroupKeyStateEvent.assembleTags(
                chatRoomId = chatRoomId,
                dkgSessionId = key.id,
                path = path
            ),
            content = thresholdPublicKey,
            key = key,
            createdAt = createdAt
        )
    }

    /**
     * Files a state the group signed, or drops it and says why.
     *
     * Storing is all this adds to [stateFrom], which is where the deciding
     * happens -- kept apart so the check a member's safety rests on can be
     * exercised without standing up a database.
     */
    suspend fun record(
        database: MantraDatabase,
        chatRoomId: String,
        innerEvent: Event
    ): GroupKeyState? =
        stateFrom(chatRoomId, innerEvent)?.let { database.groupKeyStateDao().replace(it) }

    /**
     * The state an event amounts to, or null if it amounts to none.
     *
     * Two questions, and a state has to answer both. Is it true -- does the room
     * rederive from the key it names? And did the group say it -- does the
     * signature verify against the identity of that same key?
     *
     * The first is the one that cannot be given up. Acting on a state naming a
     * key the room was not derived from means signing with a share that will not
     * aggregate, or worse, treating a key the group does not hold as the key the
     * group holds. It is also entirely independent of who is speaking: a member
     * with no share, or none of the ceremony at all, can state a true state and
     * it is still true.
     *
     * The second is what the proposal flow buys. It does not make a state truer;
     * it makes a state the group's, so that the record of what a room signs with
     * is a thing a quorum agreed to rather than a thing its creator said.
     */
    fun stateFrom(chatRoomId: String, innerEvent: Event): GroupKeyState? {
        val announced = GroupKeyStateEvent.parseChatRoomId(innerEvent.tags)
        if (announced != null && announced != chatRoomId) {
            logger.w("Key state for room $announced arrived in $chatRoomId; dropping")
            return null
        }

        val thresholdPublicKey = GroupKeyStateEvent.parseThresholdPublicKey(innerEvent.content)
        if (thresholdPublicKey == null) {
            logger.w("Key state in $chatRoomId carries no threshold key; dropping")
            return null
        }

        val dkgSessionId = GroupKeyStateEvent.parseDkgSessionId(innerEvent.tags)
        if (dkgSessionId == null) {
            logger.w("Key state in $chatRoomId names no ceremony; dropping")
            return null
        }

        val path = GroupKeyStateEvent.parsePath(innerEvent.tags)
        if (path == null) {
            logger.w("Key state in $chatRoomId carries no walkable derivation path; dropping")
            return null
        }

        // Only the group may say what the group signs with. A member holding a
        // single share cannot produce this signature, so nothing short of a
        // quorum can put a room's key state on the record -- not even a member
        // saying something true.
        if (!GroupKeyStateEvent.isSignedByGroup(innerEvent, thresholdPublicKey, path)) {
            logger.w(
                "Key state in $chatRoomId from ${innerEvent.pubKey} carries no signature by " +
                    "the group holding $thresholdPublicKey; dropping"
            )
            return null
        }

        val state = GroupKeyState(
            chatRoomId = chatRoomId,
            dkgSessionId = dkgSessionId,
            thresholdPublicKey = thresholdPublicKey,
            derivationPath = SharedKeyDerivation.formatPath(path),
            announcedBy = innerEvent.pubKey,
            announcedAt = Instant.fromEpochSeconds(innerEvent.createdAt)
        )

        // Half the trust model, in one line, and the half that does not care who
        // is speaking. Only the truth rederives the room it was stated in.
        if (!state.verifies()) {
            logger.w(
                "Key state from ${innerEvent.pubKey} names $thresholdPublicKey at " +
                    "${state.derivationPath}, which does not derive room $chatRoomId; dropping"
            )
            return null
        }

        return state
    }
}
