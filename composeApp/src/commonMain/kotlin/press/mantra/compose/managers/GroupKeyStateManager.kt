package press.mantra.compose.managers

import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import kotlin.time.Clock
import kotlin.time.Instant
import press.mantra.compose.database.MantraDatabase
import press.mantra.compose.database.model.GroupKeyState
import press.mantra.compose.database.model.MarmotInnerEvent
import press.mantra.compose.nostr.frost.GroupKeyStateEvent

/**
 * Announces and files what key a room signs with.
 *
 * The coordinator [announce]s once, as the new room's first message; every
 * other member [record]s what arrives. Both ends land on the same
 * [GroupKeyState] row, which is what a signing request is resolved against --
 * see `FrostSigningManager.completedKey`.
 *
 * Nothing here is trusted on the strength of who said it. A state is kept only
 * if the room's id rederives from the key it names, which is the same check
 * `completedKey` used to make by scanning, and the reason a coordinator cannot
 * point a room at a key it was not made from.
 */
object GroupKeyStateManager {
    private const val TAG = "GroupKeyStateManager"

    private val logger = Logger.withTag(TAG)

    /** The key state a room signs under, or null while it has none. */
    suspend fun keyStateFor(database: MantraDatabase, chatRoomId: String): GroupKeyState? =
        database.groupKeyStateDao().getByChatRoomId(chatRoomId)

    /**
     * Says what the freshly made room signs with, and files it locally.
     *
     * Called once, by the member who created the room, before anybody has been
     * added to it -- the announcement is the room's first message, so a member
     * arriving on a welcome finds it waiting rather than having to be told
     * separately.
     *
     * Queued before it is recorded, matching the signing pipeline: a crash
     * between the two costs a duplicate announcement, which [record] folds
     * away, rather than a room whose key nobody ever named.
     *
     * Refuses to announce a state that does not describe the room, because a
     * state that fails [GroupKeyState.verifies] here is this device having
     * derived the room from one key and announced another -- a bug worth
     * failing on rather than broadcasting.
     */
    suspend fun announce(
        database: MantraDatabase,
        chatRoomId: String,
        userPublicKey: HexKey,
        dkgSessionId: String,
        thresholdPublicKey: HexKey,
        path: List<Long> = SharedKeyDerivation.MARMOT_ADMIN_GROUP_PATH,
        createdAt: Long = Clock.System.now().epochSeconds
    ): GroupKeyState {
        val state = GroupKeyState(
            chatRoomId = chatRoomId,
            dkgSessionId = dkgSessionId,
            thresholdPublicKey = thresholdPublicKey,
            derivationPath = SharedKeyDerivation.formatPath(path),
            announcedBy = userPublicKey,
            announcedAt = Instant.fromEpochSeconds(createdAt)
        )

        check(state.verifies()) {
            "Room $chatRoomId is not derived from $thresholdPublicKey at ${state.derivationPath}"
        }

        val tags = GroupKeyStateEvent.assembleTags(
            chatRoomId = chatRoomId,
            dkgSessionId = dkgSessionId,
            path = path
        )

        database.marmotInnerEventDao().upsert(
            MarmotInnerEvent(
                // The rumor id the outbound pipeline will recompute from these
                // same fields when it assembles the event to encrypt.
                id = EventHasher.hashId(
                    pubKey = userPublicKey,
                    createdAt = createdAt,
                    tags = tags,
                    content = thresholdPublicKey,
                    kind = GroupKeyStateEvent.KIND
                ),
                publicKey = userPublicKey,
                kind = GroupKeyStateEvent.KIND,
                createdAt = Instant.fromEpochSeconds(createdAt),
                tags = tags,
                content = thresholdPublicKey,
                chatRoomId = chatRoomId
            )
        )

        logger.i("Announcing key ${state.thresholdPublicKey} for room $chatRoomId at ${state.derivationPath}")

        return database.groupKeyStateDao().replace(state)
    }

    /**
     * Files an inbound announcement, or drops it and says why.
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
     * The state an announcement amounts to, or null if it amounts to none.
     *
     * Every reason to return null is a reason the announcement does not describe
     * this room, and none of them are about who sent it: a member with no share,
     * or none of the ceremony at all, can announce a true state and it is still
     * true. What cannot be tolerated is a state naming a key the room was not
     * derived from, because acting on one means signing with a share that will
     * not aggregate -- or, worse, treating a key the group does not hold as the
     * key the group holds.
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

        val state = GroupKeyState(
            chatRoomId = chatRoomId,
            dkgSessionId = dkgSessionId,
            thresholdPublicKey = thresholdPublicKey,
            derivationPath = SharedKeyDerivation.formatPath(path),
            announcedBy = innerEvent.pubKey,
            announcedAt = Instant.fromEpochSeconds(innerEvent.createdAt)
        )

        // The whole trust model, in one line. Anybody may say what this room
        // signs with; only the truth rederives the room they said it in.
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
