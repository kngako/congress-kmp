package press.mantra.compose.managers

import androidx.room3.Room
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import kotlinx.coroutines.runBlocking
import press.mantra.compose.database.MantraDatabase
import press.mantra.compose.database.builder.getRoomDatabase
import press.mantra.compose.database.model.DkgSession
import press.mantra.compose.database.model.GiftWrapPayload
import press.mantra.compose.database.model.NostrEvent
import press.mantra.compose.database.model.Profile
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.database.model.types.DkgApprovalStep
import press.mantra.compose.nostr.dkg.DkgRitualEvents
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * One device, two profiles, and a group both of them are in.
 *
 * The ceremony id is minted by its coordinator and travels on every message, so it
 * is one string for every participant -- and two profiles on a device are two
 * participants. While a `DkgSession` was keyed by that id alone there was one row
 * for both of them: `acceptProposal` opened with "do I know this ceremony?", found
 * the row the first profile wrote, and returned it. The second profile then ran the
 * ritual out of the first's row -- the first's host key, the first's randomness,
 * and in the end the first's share -- while the protocol had them as two members
 * with two of each.
 *
 * Nothing here is about one profile reading the other's secrets, which it could
 * already do by being the same person on the same disk. It is about the ritual
 * being able to count. A ceremony derives each participant's FROST id from the
 * sorted host keys and hashes `n` into its own identity, so a device that
 * contributes one host key where the group expects two is a device whose shares
 * will not aggregate -- and the group finds that out at its first signature.
 *
 * Driven through the manager rather than seeded, because what is under test is the
 * write path: `proposeRitual` for the profile that opens it, `processRitualPayload`
 * for the one that receives the proposal, and `approve` for each.
 */
class TwoProfilesInOneCeremonyJvmTest {

    private val db: MantraDatabase = getRoomDatabase(
        Room.inMemoryDatabaseBuilder<MantraDatabase>()
    )

    @AfterTest
    fun closeDb() = db.close()

    // Real keys: a host key is a hash of a nostr secret and the participant order is
    // the sorted host keys, so filler exercises neither.
    private val aliceKeys = KeyPair()
    private val bobKeys = KeyPair()
    private val carolKeys = KeyPair()

    /** The two profiles signed in on this one device. */
    private val alice = aliceKeys.pubKey.toHexKey()
    private val bob = bobKeys.pubKey.toHexKey()

    /** A third member, on somebody else's device, so the group is not just this one. */
    private val carol = carolKeys.pubKey.toHexKey()

    private val quorum = 2

    private suspend fun seedProfile(publicKey: String) {
        val nostrEventId = publicKey.take(63) + "f"
        db.nostrEventDao().upsert(
            NostrEvent(
                id = nostrEventId,
                pubKey = publicKey,
                kind = 0,
                tags = emptyArray(),
                content = "{}",
                sig = "0".repeat(128),
            )
        )
        db.profileDao().upsert(
            Profile(publicKey = publicKey, userName = "member", nostrEventId = nostrEventId)
        )
    }

    /** The group's NIP-17 room, as the creation screen leaves it. */
    private suspend fun room(): LocalChatRoom {
        listOf(alice, bob, carol).forEach { seedProfile(it) }

        return assertNotNull(
            db.nostrNip17Dao().createNip17ChatRoom(
                userPublicKey = alice,
                participantPublicKeys = listOf(bob, carol),
                subject = "Two personas, one group",
            ),
            "createNip17ChatRoom returned null",
        )
    }

    /** Alice opens it, which is also her agreeing to be in it. */
    private suspend fun openAsAlice(room: LocalChatRoom): DkgSession =
        ChillDkgRitualManager.proposeRitual(
            database = db,
            localChatRoom = room,
            userPublicKey = alice,
            nostrPrivateKey = aliceKeys.privKey!!,
            threshold = quorum,
        )

    /**
     * A queued payload as the rumor its sender wrote it.
     *
     * `sig` is empty, as it is on both transports: what vouches for the author is the
     * seal, not a signature on the payload.
     */
    private fun GiftWrapPayload.asRumor(): Event = Event(
        id = id,
        pubKey = publicKey,
        createdAt = createdAt.epochSeconds,
        kind = kind,
        tags = tags,
        content = content,
        sig = "",
    )

    /** What one profile put on the wire of [kind], as the other would receive it. */
    private suspend fun queued(room: LocalChatRoom, kind: Int): List<Event> =
        db.giftWrapPayloadDao()
            .getByChatRoomAndKinds(room.chatRoom.id, listOf(kind))
            .map { it.asRumor() }

    /** The proposal as it went on the wire, which is what Bob's profile receives. */
    private suspend fun proposalIn(room: LocalChatRoom): Event = assertNotNull(
        queued(room, DkgRitualEvents.PROPOSAL).firstOrNull(),
        "the ceremony has to put a proposal on the wire",
    )

    /** Bob's profile receives it exactly as a second device would. */
    private suspend fun receiveAsBob(room: LocalChatRoom) =
        ChillDkgRitualManager.processRitualPayload(
            database = db,
            localChatRoom = room,
            innerEvent = proposalIn(room),
            userPublicKey = bob,
            nostrPrivateKey = bobKeys.privKey!!,
        )

    @Test
    fun `each profile opens a ritual row of its own`(): Unit = runBlocking {
        val room = room()
        val opened = openAsAlice(room)
        receiveAsBob(room)

        val alices = assertNotNull(
            db.dkgSessionDao().getSessionById(opened.id, alice),
            "the profile that opened the ceremony has its row",
        )
        val bobs = assertNotNull(
            db.dkgSessionDao().getSessionById(opened.id, bob),
            "and so does the profile that received the proposal",
        )

        assertEquals(opened.id, alices.id)
        assertEquals(opened.id, bobs.id, "one ceremony, named the same on both rows")
        assertEquals(alice, alices.userPublicKey)
        assertEquals(bob, bobs.userPublicKey)

        // Alice coordinates it either way, which is a fact about the ceremony rather
        // than about whose row it is.
        assertEquals(alice, alices.coordinatorPublicKey)
        assertEquals(alice, bobs.coordinatorPublicKey)
    }

    /**
     * The sharp end. Each row has to hold its own profile's secrets -- the host key
     * the group identifies that participant by, and the randomness its share is
     * rebuilt from. Sharing a row meant sharing all three.
     */
    @Test
    fun `the two rows hold different host keys and different randomness`(): Unit = runBlocking {
        val room = room()
        val opened = openAsAlice(room)
        receiveAsBob(room)

        val alices = assertNotNull(db.dkgSessionDao().getSessionById(opened.id, alice))
        val bobs = assertNotNull(db.dkgSessionDao().getSessionById(opened.id, bob))

        assertEquals(
            ChillDkgRitualManager.deriveHostPublicKey(aliceKeys.privKey!!).toString(),
            alices.hostPublicKey,
            "a profile's host key is derived from its own nostr secret",
        )
        assertEquals(
            ChillDkgRitualManager.deriveHostPublicKey(bobKeys.privKey!!).toString(),
            bobs.hostPublicKey,
        )
        assertNotEquals(
            alices.hostPublicKey,
            bobs.hostPublicKey,
            "two participants the group must be able to tell apart",
        )

        assertNotEquals(
            alices.round1Random,
            bobs.round1Random,
            "the seed a share is rebuilt from is not shared between personas",
        )
        assertNotEquals(alices.round2AuxRandom, bobs.round2AuxRandom)
    }

    /**
     * Approval is per profile, and so is what it publishes.
     *
     * Opening the ceremony is Alice agreeing to be in it, so her host key is already
     * out; Bob's profile has agreed to nothing and must have published nothing. That
     * gate is the one thing standing between a relay delivering an event to a phone
     * in a pocket and its owner being enrolled in a permanent signing quorum, and it
     * has to hold per persona rather than per device.
     */
    @Test
    fun `one profile's approval publishes only that profile's host key`(): Unit = runBlocking {
        val room = room()
        val opened = openAsAlice(room)
        receiveAsBob(room)

        assertNotNull(
            db.dkgSessionDao().getMessage(opened.id, alice, DkgRitualEvents.HOST_KEY, alice),
            "opening the ceremony published the opener's host key",
        )
        assertNull(
            db.dkgSessionDao().getSessionById(opened.id, bob)?.hostKeyApprovedAt,
            "the receiving profile has agreed to nothing yet",
        )
        assertNull(
            db.dkgSessionDao().getMessage(opened.id, bob, DkgRitualEvents.HOST_KEY, bob),
            "so nothing of theirs has gone out",
        )

        ChillDkgRitualManager.approve(
            database = db,
            localChatRoom = room,
            sessionId = opened.id,
            userPublicKey = bob,
            step = DkgApprovalStep.HOST_KEY,
            nostrPrivateKey = bobKeys.privKey!!,
        )

        val bobsOwn = assertNotNull(
            db.dkgSessionDao().getMessage(opened.id, bob, DkgRitualEvents.HOST_KEY, bob),
            "approving publishes this profile's host key",
        )
        assertEquals(
            ChillDkgRitualManager.deriveHostPublicKey(bobKeys.privKey!!).toString(),
            bobsOwn.payload,
            "and it is this profile's, not the other's",
        )

        // Alice's row is untouched by Bob approving: the approval reached one row.
        assertNotNull(db.dkgSessionDao().getSessionById(opened.id, alice)?.hostKeyApprovedAt)
    }

    /**
     * What the ritual actually asks, and the reason the messages are keyed by the
     * receiving profile as well: "have I heard from everybody" is a question about
     * one profile's view, and a count over the device's rows would answer it with
     * somebody else's mail.
     *
     * The two views fill separately. Joining replays the room's stored backlog -- a
     * ritual whose proposal loses the race to its own round-1 messages would
     * otherwise wait forever -- so Bob's profile starts with Alice's host key already
     * in it, which is right: on a second device that message would have arrived too.
     * What it does not start with is Bob's own, because Bob has agreed to nothing.
     */
    @Test
    fun `each profile counts the messages in its own view`(): Unit = runBlocking {
        val room = room()
        val opened = openAsAlice(room)
        receiveAsBob(room)

        // Alice has published hers. Bob has published nothing and holds hers, off the
        // replay, which is the backlog a real second device would have received.
        assertEquals(
            1,
            db.dkgSessionDao().countMessagesByKind(opened.id, alice, DkgRitualEvents.HOST_KEY),
        )
        assertEquals(
            1,
            db.dkgSessionDao().countMessagesByKind(opened.id, bob, DkgRitualEvents.HOST_KEY),
        )
        assertNotNull(
            db.dkgSessionDao().getMessage(opened.id, bob, DkgRitualEvents.HOST_KEY, alice),
            "and the one it holds is Alice's",
        )
        assertNull(
            db.dkgSessionDao().getMessage(opened.id, bob, DkgRitualEvents.HOST_KEY, bob),
            "not its own, which it has not agreed to publish",
        )

        ChillDkgRitualManager.approve(
            database = db,
            localChatRoom = room,
            sessionId = opened.id,
            userPublicKey = bob,
            step = DkgApprovalStep.HOST_KEY,
            nostrPrivateKey = bobKeys.privKey!!,
        )

        assertEquals(
            2,
            db.dkgSessionDao().countMessagesByKind(opened.id, bob, DkgRitualEvents.HOST_KEY),
            "Bob now holds Alice's and its own",
        )
        assertEquals(
            1,
            db.dkgSessionDao().countMessagesByKind(opened.id, alice, DkgRitualEvents.HOST_KEY),
            "and Alice is unmoved by it, not having received Bob's yet",
        )

        // The point of the key. Three message rows on this device, and no profile
        // sees three: a count over the device's rows would have had both of them
        // believing a three-member ceremony was fully subscribed.
        assertEquals(3, allHostKeyRows(opened.id), "three rows, two views")

        // And neither profile has been walked onto a rung it has not earned: a
        // three-member ceremony wants three host keys before anyone contributes to
        // the key, and nobody has Carol's.
        listOf(alice, bob).forEach { profile ->
            val session = assertNotNull(db.dkgSessionDao().getSessionById(opened.id, profile))
            assertEquals(3, session.participantCount)
            assertNull(
                ChillDkgRitualManager.pendingApproval(db, session),
                "waiting on Carol is waiting, not asking",
            )
        }
    }

    /**
     * And a message from the *other* profile lands under the receiver, which is the
     * case that used to collapse: both are participants, so each legitimately holds a
     * host-key message from the other, filed under itself.
     */
    @Test
    fun `a message from the other profile lands under the receiver`(): Unit = runBlocking {
        val room = room()
        val opened = openAsAlice(room)
        receiveAsBob(room)

        ChillDkgRitualManager.approve(
            database = db,
            localChatRoom = room,
            sessionId = opened.id,
            userPublicKey = bob,
            step = DkgApprovalStep.HOST_KEY,
            nostrPrivateKey = bobKeys.privKey!!,
        )

        // Bob's host key, as it went out, fed back to Alice's profile the way the
        // other device's would arrive.
        val bobsHostKey = assertNotNull(
            queued(room, DkgRitualEvents.HOST_KEY).firstOrNull { it.pubKey == bob },
            "Bob's approval put a host key on the wire",
        )

        ChillDkgRitualManager.processRitualPayload(
            database = db,
            localChatRoom = room,
            innerEvent = bobsHostKey,
            userPublicKey = alice,
            nostrPrivateKey = aliceKeys.privKey!!,
        )

        val received = assertNotNull(
            db.dkgSessionDao().getMessage(opened.id, alice, DkgRitualEvents.HOST_KEY, bob),
            "Alice's profile now holds Bob's host key",
        )

        // The two columns mean different things, and this is where that shows.
        assertEquals(alice, received.userPublicKey, "whose view it is")
        assertEquals(bob, received.participantPublicKey, "who sent it")
        assertEquals(
            ChillDkgRitualManager.deriveHostPublicKey(bobKeys.privKey!!).toString(),
            received.payload,
            "and the payload is the sender's host key, not the receiver's",
        )

        assertEquals(
            2,
            db.dkgSessionDao().countMessagesByKind(opened.id, alice, DkgRitualEvents.HOST_KEY),
            "two of three, so Alice is still waiting on Carol",
        )
        assertEquals(
            2,
            db.dkgSessionDao().countMessagesByKind(opened.id, bob, DkgRitualEvents.HOST_KEY),
            "and Bob still holds exactly the two it had",
        )
    }

    /** Every host-key row on the device, whichever profile's view it belongs to. */
    private suspend fun allHostKeyRows(sessionId: String): Int =
        listOf(alice, bob).sumOf {
            db.dkgSessionDao().countMessagesByKind(sessionId, it, DkgRitualEvents.HOST_KEY)
        }
}
