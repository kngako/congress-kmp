package press.mantra.compose.managers

import com.vitorpamplona.quartz.nip01Core.core.Event
import fr.acinq.bitcoin.PrivateKey
import fr.acinq.secp256k1.Hex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import press.mantra.compose.database.model.GroupKeyState
import press.mantra.compose.extensions.toHex
import press.mantra.compose.nostr.frost.GroupKeyStateEvent

/**
 * What a room's key-state announcement is allowed to convince a member of.
 *
 * The announcement is made by the coordinator, and the coordinator is untrusted
 * by construction -- the same assumption every other part of the ceremony is
 * written under. So the interesting cases here are all the ones where a state
 * is *wrong*: a member who acts on a state naming a key their room was not made
 * from signs with a share that cannot aggregate, or worse, treats a key the
 * group does not hold as the key the group holds.
 *
 * `GroupKeyStateManager` needs a database and so cannot be stood up here. What
 * can be is the check it defers to, which is where the whole trust model lives.
 */
class GroupKeyStateTest {
    /** Stands in for a ceremony's output. Any valid point will do. */
    private val thresholdPublicKey = PrivateKey(
        Hex.decode("1c0ffee0000000000000000000000000000000000000000000000000000000a1")
    ).publicKey().value.toHex()

    /** A second group's key, for the states that name the wrong one. */
    private val otherKey = PrivateKey(
        Hex.decode("2bada550000000000000000000000000000000000000000000000000000000b2")
    ).publicKey().value.toHex()

    private val path = SharedKeyDerivation.MARMOT_ADMIN_GROUP_PATH

    private val chatRoomId = SharedKeyDerivation.marmotGroupId(thresholdPublicKey, path)

    private fun state(
        chatRoomId: String = this.chatRoomId,
        thresholdPublicKey: String = this.thresholdPublicKey,
        derivationPath: String = SharedKeyDerivation.formatPath(path)
    ) = GroupKeyState(
        chatRoomId = chatRoomId,
        dkgSessionId = "ceremony-1",
        thresholdPublicKey = thresholdPublicKey,
        derivationPath = derivationPath,
        announcedBy = "c00rd1na70r",
        announcedAt = Instant.fromEpochSeconds(1_700_000_000)
    )

    @Test
    fun `a state describing the room it was announced in verifies`() {
        assertTrue(state().verifies())
    }

    @Test
    fun `a state naming another group's key does not verify`() {
        // The attack this is here for: a coordinator pointing the room at a key
        // the group never made, so that everything signed in it is signed by
        // whoever holds that key instead.
        assertFalse(state(thresholdPublicKey = otherKey).verifies())
    }

    @Test
    fun `a state naming the right key at the wrong path does not verify`() {
        // The path is half the derivation, so getting it wrong reaches a
        // different room just as surely as getting the key wrong does.
        assertFalse(state(derivationPath = "m/9420/0/1").verifies())
    }

    @Test
    fun `a state for one room does not verify against another`() {
        assertFalse(state(chatRoomId = otherKey).verifies())
    }

    @Test
    fun `a state carrying an unwalkable path does not verify`() {
        // Hardened derivation needs the parent private key, which nobody in a
        // threshold group has, so a hardened path was never walked to anything.
        assertFalse(state(derivationPath = "m/9420'/0/0").verifies())
        assertFalse(state(derivationPath = "9420/0/0").verifies())
        assertFalse(state(derivationPath = "").verifies())
    }

    @Test
    fun `the tags a state is announced on read back as they were written`() {
        val tags = GroupKeyStateEvent.assembleTags(
            chatRoomId = chatRoomId,
            dkgSessionId = "ceremony-1",
            path = path
        )

        assertEquals(chatRoomId, GroupKeyStateEvent.parseChatRoomId(tags))
        assertEquals("ceremony-1", GroupKeyStateEvent.parseDkgSessionId(tags))
        assertEquals(path, GroupKeyStateEvent.parsePath(tags))
    }

    @Test
    fun `a threshold key is only read out of content that is one`() {
        assertEquals(thresholdPublicKey, GroupKeyStateEvent.parseThresholdPublicKey(thresholdPublicKey))

        // 32 bytes is an x-only key, not the 33-byte compressed point a ceremony
        // reports; anything else is not a key at all.
        assertNull(GroupKeyStateEvent.parseThresholdPublicKey(chatRoomId))
        assertNull(GroupKeyStateEvent.parseThresholdPublicKey(""))
        assertNull(GroupKeyStateEvent.parseThresholdPublicKey("not a key"))
        assertNull(GroupKeyStateEvent.parseThresholdPublicKey("z".repeat(66)))
    }

    @Test
    fun `a path survives the trip through a tag`() {
        val deep = listOf(9420L, 7L, 0L, 1L)
        val tags = GroupKeyStateEvent.assembleTags(chatRoomId, "ceremony-1", deep)

        assertEquals(deep, GroupKeyStateEvent.parsePath(tags))
    }

    @Test
    fun `a room derived at a path other than the default still verifies at it`() {
        // The reason the path is announced rather than assumed: a lookup that
        // hardcodes MARMOT_ADMIN_GROUP_PATH cannot find this room at all.
        val sibling = listOf(9420L, 0L, 1L)
        val siblingRoom = SharedKeyDerivation.marmotGroupId(thresholdPublicKey, sibling)

        assertTrue(
            state(
                chatRoomId = siblingRoom,
                derivationPath = SharedKeyDerivation.formatPath(sibling)
            ).verifies()
        )
    }

    // ---- The announcement as it actually arrives -------------------------
    //
    // Everything above checks the verdict on a state already assembled. These
    // check the assembling: a real Event, with the tags and content an
    // announcement is carried on, through the function the inbound path calls.

    private fun announcement(
        content: String = thresholdPublicKey,
        tags: Array<Array<String>> = GroupKeyStateEvent.assembleTags(chatRoomId, "ceremony-1", path),
        pubKey: String = "c00rd1na70r",
        createdAt: Long = 1_700_000_000
    ) = Event(
        id = "an-id",
        pubKey = pubKey,
        createdAt = createdAt,
        kind = GroupKeyStateEvent.KIND,
        tags = tags,
        content = content,
        sig = ""
    )

    @Test
    fun `an announcement of the room it arrives in is taken`() {
        val state = GroupKeyStateManager.stateFrom(chatRoomId, announcement())

        assertEquals(chatRoomId, state?.chatRoomId)
        assertEquals("ceremony-1", state?.dkgSessionId)
        assertEquals(thresholdPublicKey, state?.thresholdPublicKey)
        assertEquals("m/9420/0/0", state?.derivationPath)
        // Attribution and ordering come off the event, not off the clock.
        assertEquals("c00rd1na70r", state?.announcedBy)
        assertEquals(Instant.fromEpochSeconds(1_700_000_000), state?.announcedAt)
    }

    @Test
    fun `an announcement naming another group's key is dropped`() {
        // The one that matters: a coordinator pointing the room at a key the
        // group never made. Everything else here is malformed input; this is
        // well-formed input that lies.
        assertNull(GroupKeyStateManager.stateFrom(chatRoomId, announcement(content = otherKey)))
    }

    @Test
    fun `an announcement addressed to another room is dropped`() {
        val elsewhere = GroupKeyStateEvent.assembleTags(otherKey, "ceremony-1", path)

        assertNull(GroupKeyStateManager.stateFrom(chatRoomId, announcement(tags = elsewhere)))
    }

    @Test
    fun `an announcement missing any of what it has to say is dropped`() {
        val full = GroupKeyStateEvent.assembleTags(chatRoomId, "ceremony-1", path)

        // No ceremony to reach a share through.
        assertNull(
            GroupKeyStateManager.stateFrom(
                chatRoomId,
                announcement(tags = full.filterNot { it[0] == "frost_key" }.toTypedArray())
            )
        )
        // No path, so nothing to rebuild a TweakCache from.
        assertNull(
            GroupKeyStateManager.stateFrom(
                chatRoomId,
                announcement(tags = full.filterNot { it[0] == "frost_path" }.toTypedArray())
            )
        )
        // No key.
        assertNull(GroupKeyStateManager.stateFrom(chatRoomId, announcement(content = "")))
    }

    @Test
    fun `an announcement carrying no d tag is judged on its derivation alone`() {
        // The d tag is a convenience for a reader holding the event on its own.
        // Dropping it loses nothing that matters, because the room it arrived in
        // plus the derivation still settle the question.
        val undirected = arrayOf(
            arrayOf("frost_key", "ceremony-1"),
            arrayOf("frost_path", "m/9420/0/0")
        )

        assertEquals(
            chatRoomId,
            GroupKeyStateManager.stateFrom(chatRoomId, announcement(tags = undirected))?.chatRoomId
        )
    }

    @Test
    fun `a path index wider than a uint32 is not a path`() {
        // tweakScalar serialises an index as its low four bytes, so m/4294967296
        // would otherwise walk exactly where m/0 does -- one room, two spellings,
        // both verifying. Rejected at the parser so formatPath stays a round trip.
        assertNull(SharedKeyDerivation.parsePathString("m/4294967296/0/0"))
        assertNull(SharedKeyDerivation.parsePathString("m/-1/0/0"))

        assertEquals(listOf(4294967295L), SharedKeyDerivation.parsePathString("m/4294967295"))
        assertEquals(listOf(0L), SharedKeyDerivation.parsePathString("m/0"))
    }

    @Test
    fun `a state whose path indices are out of range does not verify`() {
        // Reachable only by constructing the row directly; the parser above
        // refuses to build one. Checked because verifies() is what everything
        // else defers to, and it should not be the thing that trusts its input.
        assertFalse(state(derivationPath = "m/4294967296/0/0").verifies())
    }
}
