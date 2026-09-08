package press.mantra.compose.managers

import androidx.room3.Room
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import kotlinx.coroutines.runBlocking
import press.mantra.compose.database.MantraDatabase
import press.mantra.compose.database.builder.getRoomDatabase
import press.mantra.compose.database.model.ChatMessage
import press.mantra.compose.database.model.NostrEvent
import press.mantra.compose.database.model.Profile
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.database.model.types.DkgRitualStage
import press.mantra.compose.nostr.dkg.DkgRitualEvents
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What a group gets for choosing "robust": the key ceremony its room opens with.
 *
 * The quorum picked while creating the group has nothing in NIP-17 to govern --
 * there is no group state to change, and a different member set is simply a
 * different room -- so the only thing that can carry it is a t-of-n key the
 * members generate together. Everything that ceremony needs is settled the
 * moment the room exists, so `SelectChatRoomTypeViewModel.createNip17ChatRoom`
 * opens one there and then rather than leaving it for somebody to find a button
 * for. These are the properties that has to hold to.
 *
 * The proposal being the room's *first* event is not a nicety. Standing up a
 * NIP-17 room sends nothing to anybody, so until something goes out the group
 * exists on one device only; the proposal is what the other members hear the
 * room from at all, which is why `NostrDao` builds the room from a DKG payload's
 * p-tags on the way in.
 *
 * The view model itself is not exercised here -- it needs a wallet's key manager
 * and a Compose runtime -- so what stays uncovered is the wiring: that the
 * quorum reaching [ChillDkgRitualManager.proposeRitual] is the one the picker
 * holds, and that a null session leaves the user on the creation screen.
 */
class RobustRoomKeyCeremonyTest {

    private val db: MantraDatabase = getRoomDatabase(
        Room.inMemoryDatabaseBuilder<MantraDatabase>()
    )

    @AfterTest
    fun closeDb() = db.close()

    // Real keys throughout: the room id is derived by doing point work over the
    // member set, and the host key each member is identified by for the ceremony
    // is derived from their nostr secret. Hex filler exercises neither.
    private val creator = KeyPair()
    private val user = creator.pubKey.toHexKey()
    private val creatorPrivateKey = creator.privKey!!
    private val alice = KeyPair().pubKey.toHexKey()
    private val bob = KeyPair().pubKey.toHexKey()

    /** The quorum a three-member group is offered by default, and picks here. */
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

    /** The room the creation screen leaves behind for a robust group. */
    private suspend fun robustRoom(): LocalChatRoom {
        listOf(user, alice, bob).forEach { seedProfile(it) }

        return assertNotNull(
            db.nostrNip17Dao().createNip17ChatRoom(
                userPublicKey = user,
                participantPublicKeys = listOf(alice, bob),
                subject = "Robust Group",
            ),
            "createNip17ChatRoom returned null",
        )
    }

    private suspend fun openCeremony(
        room: LocalChatRoom,
        threshold: Int = quorum,
        parentChatRoomId: String? = null,
    ) =
        ChillDkgRitualManager.proposeRitual(
            database = db,
            localChatRoom = room,
            userPublicKey = user,
            nostrPrivateKey = creatorPrivateKey,
            threshold = threshold,
            parentChatRoomId = parentChatRoomId,
        )

    private suspend fun ritualPayloads(room: LocalChatRoom) =
        db.giftWrapPayloadDao().getByChatRoomAndKinds(room.chatRoom.id, DkgRitualEvents.ALL.toList())

    /** The room's lines in the order they were written, id being the insertion order. */
    private suspend fun chatMessages(room: LocalChatRoom) =
        db.chatMessageDao().getChatMessagesByChatRoomId(room.chatRoom.id)
            .map { it.chatMessage }
            .sortedBy { it.id }

    /**
     * The whole of the change: a robust room has nothing in it until the ceremony,
     * and the ceremony is the first thing in it.
     */
    @Test
    fun `a robust room's first message is the key ceremony it opens with`() = runBlocking {
        val room = robustRoom()

        assertTrue(chatMessages(room).isEmpty(), "the room says nothing until something is sent")
        assertTrue(ritualPayloads(room).isEmpty(), "and nothing has gone out yet")

        val session = openCeremony(room)

        assertEquals(
            ChatMessage.TYPE_DKG_STARTED,
            chatMessages(room).first().messageType,
            "the first line in a robust room is its ceremony opening",
        )

        val proposal = ritualPayloads(room).first()
        assertEquals(DkgRitualEvents.PROPOSAL, proposal.kind, "and the first event out is the proposal")
        assertEquals(user, proposal.publicKey)

        // What every receiver rebuilds the ceremony from: `n` is the p-tag set plus
        // the sender, and `t` is the quorum the group was created on. Both are read
        // off this event rather than off the receiver's own view of the room, which
        // is the thing that drifts.
        assertEquals(
            setOf(user, alice, bob),
            proposal.participantPTags().map { it.pubKey }.toSet(),
            "the proposal carries the whole membership",
        )
        assertEquals(quorum, DkgRitualEvents.parseThreshold(proposal.tags))
        assertEquals(session.id, DkgRitualEvents.parseSessionId(proposal.tags))

        assertEquals(quorum, session.threshold, "the ceremony runs at the quorum that was picked")
        assertEquals(3, session.participantCount)
        assertEquals(user, session.coordinatorPublicKey, "the room's creator coordinates")

        // Opening a ceremony is the act of agreeing to be in it, so this member is
        // not asked again -- and their host key goes out with the proposal.
        assertNotNull(session.hostKeyApprovedAt)
        assertEquals(
            1,
            ritualPayloads(room).count { it.kind == DkgRitualEvents.HOST_KEY },
            "the coordinator joins the ceremony it opened",
        )
    }

    /**
     * Creation is re-enterable -- the room id is derived from the member set, so the
     * same group made twice is the same room -- and the ceremony has to be
     * re-enterable with it. A second proposal is a second participant set for every
     * member to reconcile, and the key the first one produced would be left with
     * nothing pointing at it.
     */
    @Test
    fun `making the same group twice does not open a second ceremony`() = runBlocking {
        val room = robustRoom()
        val first = openCeremony(room)

        val again = assertNotNull(
            db.nostrNip17Dao().createNip17ChatRoom(
                userPublicKey = user,
                participantPublicKeys = listOf(alice, bob),
                subject = "Robust Group",
            )
        )
        assertEquals(room.chatRoom.id, again.chatRoom.id, "the same members are the same room")

        // Asked for a different quorum this time: the running ceremony settled that
        // question when it opened, and answers with itself rather than the ask.
        val second = openCeremony(again, threshold = 3)

        assertEquals(first.id, second.id)
        assertEquals(quorum, second.threshold)
        assertEquals(
            1,
            ritualPayloads(room).count { it.kind == DkgRitualEvents.PROPOSAL },
            "one proposal, not two",
        )
        assertEquals(
            1,
            chatMessages(room).count { it.messageType == ChatMessage.TYPE_DKG_STARTED },
            "and the room is told once",
        )
    }

    /**
     * A collapsed ceremony leaves the group with no key and a room they can still
     * talk in, which is exactly the group that should be able to try again. Failed
     * is the one state that does not hold the room's ceremony slot.
     */
    @Test
    fun `a failed ceremony is replaced rather than handed back`() = runBlocking {
        val room = robustRoom()
        val abandoned = openCeremony(room)

        db.dkgSessionDao().upsert(abandoned.copy(stage = DkgRitualStage.FAILED))

        val replacement = openCeremony(room)

        assertNotEquals(abandoned.id, replacement.id)
        assertEquals(DkgRitualStage.COLLECTING_HOST_KEYS, replacement.stage)
        assertEquals(
            2,
            ritualPayloads(room).count { it.kind == DkgRitualEvents.PROPOSAL },
            "the group proposes again",
        )
    }

    /**
     * The room's name reached nobody but the member who typed it.
     *
     * `NostrDao.getOrCreateNip17ChatRoom` has always built the receiving side's
     * room with `subject = parseSubject()`, and nothing on the ritual path ever
     * wrote a subject tag. Standing up a NIP-17 room sends nothing to anybody, so
     * the proposal is routinely the first anyone hears of the room -- and it
     * arrived untitled, with a key ceremony already running in it.
     */
    @Test
    fun `the proposal carries the room's name, and no later message repeats it`() = runBlocking {
        val room = robustRoom()
        openCeremony(room)

        val payloads = ritualPayloads(room)
        val proposal = payloads.first { it.kind == DkgRitualEvents.PROPOSAL }

        assertEquals("Robust Group", DkgRitualEvents.parseSubject(proposal.tags))

        // Every later message belongs to a session the receiver already has the
        // room for, so repeating the name would be bytes per round to say
        // something already settled.
        payloads.filterNot { it.kind == DkgRitualEvents.PROPOSAL }.forEach {
            assertNull(
                DkgRitualEvents.parseSubject(it.tags),
                "kind ${it.kind} should not repeat the room's name",
            )
        }
    }

    @Test
    fun `an ordinary ceremony claims no parent anywhere`() = runBlocking {
        val room = robustRoom()
        val session = openCeremony(room)

        assertNull(session.parentChatRoomId)
        ritualPayloads(room).forEach {
            assertNull(
                DkgRitualEvents.parseParentChatRoomId(it.tags),
                "kind ${it.kind} should claim no parent",
            )
        }
    }

    /**
     * A subgroup ceremony says what it is for, and says it once.
     *
     * The claim is unverified and nothing is granted on it -- the participant set
     * is still the p-tags and the threshold is still the threshold. What it buys
     * is that a member selected for a subgroup is not left watching a key ceremony
     * open in a room they did not ask for with nothing saying why. The parent's own
     * signature turns up two steps later, on the birth certificate.
     */
    @Test
    fun `a subgroup ceremony names its parent, on the proposal alone`() = runBlocking {
        val room = robustRoom()
        val parent = "ab".repeat(32)

        val session = openCeremony(room, parentChatRoomId = parent)

        assertEquals(parent, session.parentChatRoomId)

        val payloads = ritualPayloads(room)
        val proposal = payloads.first { it.kind == DkgRitualEvents.PROPOSAL }

        assertEquals(parent, DkgRitualEvents.parseParentChatRoomId(proposal.tags))

        // Only on the proposal: a later message with a say in what an opened
        // ceremony is for would be a second answer to a settled question.
        payloads.filterNot { it.kind == DkgRitualEvents.PROPOSAL }.forEach {
            assertNull(
                DkgRitualEvents.parseParentChatRoomId(it.tags),
                "kind ${it.kind} should not restate the parent",
            )
        }

        // And it changes nothing about the ceremony itself.
        assertEquals(quorum, session.threshold)
        assertEquals(3, session.participantCount)
    }
}
