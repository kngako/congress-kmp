package press.mantra.compose.managers

import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroup
import press.mantra.compose.extensions.toHex
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps a room's [MlsGroup] alive between messages instead of rebuilding it
 * from the stored state every time.
 *
 * ### The bug this exists for
 *
 * MLS is specified to tolerate out-of-order delivery within an epoch: a
 * receiver that gets generation N+1 before N derives and caches the key for N
 * so the older message can still be read when it turns up. Quartz's
 * `SecretTree` does exactly that, in a private `skippedKeys` map.
 *
 * `SecretTree.exportSenderStates()` does not include that map, so
 * `MlsGroup.saveState()` does not carry it. Rebuilding the group from stored
 * state therefore throws the skipped keys away, and a message for a generation
 * the ratchet has already passed fails
 * `require(generation >= state.applicationGeneration)` and is dropped. There is
 * no recovering it afterwards: the key is gone and the sender will not resend.
 *
 * Nostr relays offer no ordering whatsoever, so this is not an edge case. Two
 * events published in the same second race, and exactly one survives — which is
 * how a signing session's proposal was lost while the nonce sent immediately
 * behind it arrived fine.
 *
 * ### What this fixes, and what it does not
 *
 * Holding the instance means `skippedKeys` survives for as long as the process
 * does and nothing else writes the room's state. That covers the case that
 * actually bites — a burst of messages arriving in one sync — because they are
 * decrypted one after another against the same tree.
 *
 * It does not survive a restart, and it does not survive another writer, so
 * reordering across app launches still loses messages. The real fix is for
 * `exportSenderStates` to carry the skipped keys; see
 * `docs/mls-skipped-keys.md`.
 *
 * ### Staleness
 *
 * The group is only reused when the stored state is still exactly what this
 * cache last wrote. Anything else that saves a room's state — sending a message
 * advances the sender ratchet and saves, so does adding a member — changes the
 * hex, and the next read rebuilds rather than carrying on from a group that has
 * been overtaken. Losing the skipped keys there is the same behaviour as
 * before this existed, so the fallback is never worse than not caching.
 */
object MlsGroupCache {
    private const val TAG = "MlsGroupCache"

    private val logger = Logger.withTag(TAG)

    private class Entry(
        val group: MlsGroup,
        /** The state hex this cache last wrote, for spotting another writer. */
        var stateHex: String,
    )

    private val entries = mutableMapOf<String, Entry>()

    /**
     * Serialises use of one room's group.
     *
     * The group is mutable and decryption advances it, so two events for the
     * same room being decrypted at once would corrupt the ratchet. One lock per
     * room rather than one overall, so a busy room cannot hold up a quiet one.
     *
     * Held across database work, which is safe here because a caller only ever
     * takes this lock while it is already running -- it never waits on a
     * resource the holder is waiting for.
     */
    private val locks = mutableMapOf<String, Mutex>()
    private val locksGuard = Mutex()

    private suspend fun lockFor(chatRoomId: String): Mutex =
        locksGuard.withLock { locks.getOrPut(chatRoomId) { Mutex() } }

    /**
     * Runs [block] against the room's live group, then stores whatever state it
     * left behind.
     *
     * [storedStateHex] is the room's state as the database currently has it, and
     * [build] turns it into a group. [save] is handed the state to persist; it
     * runs inside the lock so the stored state and the cached instance cannot
     * disagree.
     *
     * Returns null without calling [block] when the room has no usable group
     * state, which is the same thing a failed `toMlsGroup()` meant before.
     */
    suspend fun <T> withGroup(
        chatRoomId: String,
        storedStateHex: String?,
        build: () -> MlsGroup?,
        save: suspend (String) -> Unit,
        block: suspend (MlsGroup) -> T,
    ): T? = lockFor(chatRoomId).withLock {
        val cached = entries[chatRoomId]

        val group = if (cached != null && cached.stateHex == storedStateHex) {
            cached.group
        } else {
            if (cached != null) {
                logger.d("Room $chatRoomId was written elsewhere; rebuilding its group")
            }
            build() ?: return@withLock null
        }

        val result = block(group)

        val stateHex = group.saveState().encodeTls().toHex()
        save(stateHex)
        entries[chatRoomId] = Entry(group = group, stateHex = stateHex)

        result
    }
}
