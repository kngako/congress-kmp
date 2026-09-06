package press.mantra.compose.managers

import androidx.room3.Room
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import com.vitorpamplona.quartz.nip01Core.crypto.Nip01Crypto
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.PrivateKey
import fr.acinq.bitcoin.XonlyPublicKey
import fr.acinq.bitcoin.crypto.frost.Frost
import fr.acinq.bitcoin.crypto.frost.KeyMaterial
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import press.mantra.compose.database.MantraDatabase
import press.mantra.compose.database.builder.getRoomDatabase
import press.mantra.compose.database.model.ChatRoom
import press.mantra.compose.database.model.DkgParticipantMessage
import press.mantra.compose.database.model.DkgSession
import press.mantra.compose.database.model.FrostSigningSession
import press.mantra.compose.database.model.NostrEvent
import press.mantra.compose.database.model.Profile
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.database.model.types.DkgRitualStage
import press.mantra.compose.database.model.types.FrostSigningStage
import press.mantra.compose.extensions.toHex
import press.mantra.compose.nostr.dkg.DkgRitualEvents
import press.mantra.compose.nostr.frost.FrostSigningEvents
import press.mantra.compose.nostr.frost.GroupKeyStateEvent
import press.mantra.compose.nostr.nip30303.DialectEvent

/**
 * Two devices, two databases, one real signing session, and the wire between
 * them held by hand.
 *
 * Everything else about signing is checked in pieces: `FrostSigningRoundTest`
 * runs the FROST calls, `GroupKeyStateTest` judges a finished state, and
 * `SignedArtifactTest` turns a signature into rows. None of them can see the
 * claims that only exist *between* devices, and those are exactly what this
 * change is made of:
 *
 *  - a room's key state is not written by whoever creates the room, it is
 *    signed into existence by a quorum and lands on every device at once;
 *  - both devices resolve the room's derivation path independently and arrive
 *    at the same event id, without either being told what it is;
 *  - what they sign as is the room's own key, so the author on the finished
 *    event is the id of the room it was signed in.
 *
 * The transport is the only thing faked. `FrostSigningManager.broadcast` queues
 * a `MarmotInnerEvent` for the outbound pipeline to encrypt, so ferrying those
 * rows between two databases *is* the group, minus MLS -- which has no opinion
 * about any of the above.
 */
class SignedGroupKeyStateTest {
    private val participants = 3
    private val threshold = 2

    /** Stands in for a completed ceremony; the test is about what happens after one. */
    private val keyMaterial: KeyMaterial = Frost.trustedDealerKeygen(
        thresholdSecretKey = PrivateKey(
            ByteVector32("1c0ffee0000000000000000000000000000000000000000000000000000000a1")
        ),
        nParticipants = participants,
        threshold = threshold
    )

    private val thresholdPublicKey = keyMaterial.thresholdPublicKey.value.toHex()

    /** The group's root key as nostr would name it -- what nothing should be signed as. */
    private val rootPublicKey = XonlyPublicKey(keyMaterial.thresholdPublicKey).value.toHex()

    private val ceremonyId = "ceremony".padEnd(64, '0')

    /** The admin room: the group's key walked to the admin path, which is its id. */
    private val adminRoomId = SharedKeyDerivation.marmotGroupId(thresholdPublicKey)

    private val adminRoomDescription = SharedKeyDerivation.describe("Admins of the group.")

    /**
     * The members, in the order the ceremony placed them.
     *
     * `FrostSigningManager.signerIds` derives a member's FROST id from the
     * bytewise sort of the ceremony's host keys, so these are chosen to sort the
     * same way as the list reads -- member 0 holds `secretShares[0]`, and so on.
     */
    private val members = listOf("a", "b", "c").map { it.repeat(64) }
    private val hostKeys = listOf("2a", "2b", "2c").map { it.padEnd(66, '0') }

    private val devices = mutableListOf<Device>()

    @AfterTest
    fun closeDatabases() = devices.forEach { it.db.close() }

    /**
     * One member's device: their own database, their own share, their own view
     * of the room. Nothing is shared between two of these but what is ferried.
     */
    private inner class Device(
        val publicKey: HexKey,
        val db: MantraDatabase,
        val room: LocalChatRoom
    ) {
        /**
         * Inner events this device has already been handed, so a pump terminates.
         *
         * Kept on the receiver rather than the sender because a message goes to
         * every other device: one sender-side set would let the first delivery
         * hide the message from everybody else, which is a bug in the wire and
         * not in the group.
         */
        val received = mutableSetOf<String>()

        val roomId: String get() = room.chatRoom.id

        suspend fun session(sessionId: String): FrostSigningSession? =
            db.frostSigningSessionDao().getSessionById(sessionId)

        suspend fun keyState() = db.groupKeyStateDao().getByChatRoomId(roomId)
    }

    private suspend fun device(
        publicKey: HexKey,
        signerIndex: Int,
        roomId: String = adminRoomId,
        description: String? = adminRoomDescription,
        ceremonyRoomId: String = roomId
    ): Device {
        val db = getRoomDatabase(Room.inMemoryDatabaseBuilder<MantraDatabase>())

        // Profile hangs off a nostr event, and a room off a profile. Neither is
        // anything this test is about; they are the foreign keys in the way.
        val nostrEventId = "e$publicKey".take(64)
        db.nostrEventDao().upsert(
            NostrEvent(
                id = nostrEventId,
                pubKey = publicKey,
                kind = 0,
                tags = emptyArray(),
                content = "{}",
                sig = "0".repeat(128)
            )
        )
        db.profileDao().upsert(Profile(publicKey = publicKey, nostrEventId = nostrEventId))

        val chatRoom = ChatRoom(
            id = roomId,
            userPublicKey = publicKey,
            subject = "#admins",
            description = description,
            mlsGroupState = null
        )
        db.chatRoomDao().upsert(chatRoom)

        db.dkgSessionDao().upsert(
            DkgSession(
                id = ceremonyId,
                chatRoomId = ceremonyRoomId,
                coordinatorPublicKey = members.first(),
                userPublicKey = publicKey,
                threshold = threshold,
                participantCount = participants,
                stage = DkgRitualStage.COMPLETE,
                hostPublicKey = hostKeys[signerIndex],
                round1Random = "1".repeat(64),
                round2AuxRandom = "2".repeat(64),
                thresholdPublicKey = thresholdPublicKey,
                secretShare = keyMaterial.secretShares[signerIndex].value.toHex(),
                publicShares = keyMaterial.publicShares.joinToString(",") { it.value.toHex() }
            )
        )

        // The host keys every device holds, which is what places a signer in the
        // ceremony's order. Derived rather than stored, so both devices have to
        // agree on this or their partial signatures land in the wrong slots.
        members.forEachIndexed { index, member ->
            db.dkgSessionDao().upsert(
                DkgParticipantMessage(
                    sessionId = ceremonyId,
                    participantPublicKey = member,
                    kind = DkgRitualEvents.HOST_KEY,
                    payload = hostKeys[index]
                )
            )
        }

        return Device(publicKey, db, LocalChatRoom(chatRoom = chatRoom))
            .also { devices += it }
    }

    private suspend fun ceremonyOn(device: Device): DkgSession =
        device.db.dkgSessionDao().getSessionById(ceremonyId)!!

    /** Every protocol row this device has queued, oldest first, protocol order within a second. */
    private suspend fun outbox(device: Device) = device.db.marmotInnerEventDao()
        .getByChatRoomAndKinds(
            chatRoomId = device.roomId,
            kinds = (FrostSigningEvents.ALL + GroupKeyStateEvent.KIND).toList()
        )
        .filter { it.publicKey == device.publicKey }
        // Two rows queued in the same second tie on createdAt. Kind breaks it in
        // the order a session runs, which is what a relay would have preserved.
        .sortedWith(compareBy({ it.createdAt }, { it.kind }))

    /**
     * Hands everything one device has queued to the other, until neither has
     * anything left.
     *
     * The payload is stored before it is dispatched, which is not incidental:
     * that is what the real inbound path does, and it is what lets a message
     * arriving before the proposal it belongs to be replayed afterwards rather
     * than lost.
     */
    private suspend fun pump(vararg between: Device) {
        var moved = true
        while (moved) {
            moved = false

            for (from in between) {
                for (to in between) {
                    if (from === to) continue

                    outbox(from).forEach { queued ->
                        if (!to.received.add(queued.id)) return@forEach
                        moved = true

                        to.db.marmotInnerEventDao().upsert(queued)
                        FrostSigningManager.processSigningPayload(
                            database = to.db,
                            localChatRoom = to.room,
                            innerEvent = Event(
                                id = queued.id,
                                pubKey = queued.publicKey,
                                createdAt = queued.createdAt.epochSeconds,
                                kind = queued.kind,
                                tags = queued.tags,
                                content = queued.content,
                                sig = ""
                            ),
                            userPublicKey = to.publicKey
                        )
                    }
                }
            }
        }
    }

    // ---- The key state, from proposal to row ------------------------------

    @Test
    fun `creating a room proposes its key state rather than announcing it`() = runBlocking {
        val creator = device(members[0], signerIndex = 0)

        val session = GroupKeyStateManager.propose(
            database = creator.db,
            localChatRoom = creator.room,
            userPublicKey = creator.publicKey,
            key = ceremonyOn(creator)
        )

        // The whole change: the creator has decided nothing. Until a quorum
        // signs, the room has no key state at all -- not even on the device that
        // asked for one.
        assertNull(creator.keyState(), "propose must write no state of its own")

        val queued = outbox(creator)
        val proposal = queued.firstOrNull { it.kind == FrostSigningEvents.PROPOSAL }
        assertNotNull(proposal, "the room's first message must be a signing proposal")

        val payload = Event.fromJson(proposal.content)
        assertEquals(GroupKeyStateEvent.KIND, payload.kind)
        assertEquals(thresholdPublicKey, payload.content)
        assertEquals(session.eventId, payload.id)
    }

    @Test
    fun `the key state a room proposes is authored by the room`() = runBlocking {
        val creator = device(members[0], signerIndex = 0)

        val session = GroupKeyStateManager.propose(
            database = creator.db,
            localChatRoom = creator.room,
            userPublicKey = creator.publicKey,
            key = ceremonyOn(creator)
        )

        val payload = Event.fromJson(session.unsignedEventJson)

        // Signing runs at the room's derivation path, so the key the group signs
        // as is the room's own id. Not the group's root key, which is what an
        // untweaked cache would produce and what this used to be.
        assertEquals(adminRoomId, payload.pubKey)
        assertTrue(payload.pubKey != rootPublicKey)
        assertEquals(SharedKeyDerivation.formatPath(), session.derivationPath)
    }

    @Test
    fun `a second device reaches the same event without being told the path`() = runBlocking {
        val creator = device(members[0], signerIndex = 0)
        // No description on this one, so its path cannot come from the room's
        // metadata and has to be resolved and checked against the room's id.
        val other = device(members[1], signerIndex = 1, description = null)

        val session = GroupKeyStateManager.propose(
            database = creator.db,
            localChatRoom = creator.room,
            userPublicKey = creator.publicKey,
            key = ceremonyOn(creator)
        )
        pump(creator, other)

        val received = other.session(session.id)
        assertNotNull(received, "the proposal should have opened a session on the other device")

        // Rebuilt from the event's own fields under this device's own reading of
        // the room. Agreeing on the id is agreeing on every byte that is signed,
        // the author included.
        assertEquals(session.eventId, received.eventId)
        assertEquals(session.derivationPath, received.derivationPath)
    }

    @Test
    fun `nothing of a member's own goes out before they approve`() = runBlocking {
        val creator = device(members[0], signerIndex = 0)
        val other = device(members[1], signerIndex = 1)

        val session = GroupKeyStateManager.propose(
            database = creator.db,
            localChatRoom = creator.room,
            userPublicKey = creator.publicKey,
            key = ceremonyOn(creator)
        )
        pump(creator, other)

        assertTrue(FrostSigningManager.isAwaitingApproval(other.session(session.id)!!))
        assertTrue(
            outbox(other).isEmpty(),
            "a device that has not been asked yet must publish nothing"
        )
        assertNull(other.keyState())
    }

    @Test
    fun `a quorum signing it puts the same state on every device`() = runBlocking {
        val creator = device(members[0], signerIndex = 0)
        val other = device(members[1], signerIndex = 1)

        val session = GroupKeyStateManager.propose(
            database = creator.db,
            localChatRoom = creator.room,
            userPublicKey = creator.publicKey,
            key = ceremonyOn(creator)
        )
        pump(creator, other)

        FrostSigningManager.approve(other.db, other.room, session.id)
        pump(creator, other)

        assertEquals(FrostSigningStage.COMPLETE, creator.session(session.id)?.stage)
        assertEquals(FrostSigningStage.COMPLETE, other.session(session.id)?.stage)

        // The payoff. Two devices, neither of which was sent a state, both
        // holding the same one because both applied the signature themselves.
        listOf(creator, other).forEach { device ->
            val state = device.keyState()
            assertNotNull(state, "every signer should hold the state the group signed")
            assertEquals(thresholdPublicKey, state.thresholdPublicKey)
            assertEquals(ceremonyId, state.dkgSessionId)
            assertEquals(SharedKeyDerivation.formatPath(), state.derivationPath)
            // Signed by the room, so attributed to the room -- nobody in
            // particular said this.
            assertEquals(adminRoomId, state.announcedBy)
            assertTrue(state.verifies())
        }
    }

    @Test
    fun `the signature on the finished state verifies against the room's id`() = runBlocking {
        val creator = device(members[0], signerIndex = 0)
        val other = device(members[1], signerIndex = 1)

        val session = GroupKeyStateManager.propose(
            database = creator.db,
            localChatRoom = creator.room,
            userPublicKey = creator.publicKey,
            key = ceremonyOn(creator)
        )
        pump(creator, other)
        FrostSigningManager.approve(other.db, other.room, session.id)
        pump(creator, other)

        val signed = FrostSigningManager.signedEvent(creator.session(session.id)!!)
        assertNotNull(signed, "a completed session must carry a signed event")

        assertEquals(adminRoomId, signed.pubKey)
        assertTrue(
            Nip01Crypto.verify(
                signature = signed.sig.hexToByteArray(),
                hash = signed.id.hexToByteArray(),
                pubKey = adminRoomId.hexToByteArray()
            ),
            "the room's key state must be signed by the room's own key"
        )

        // And it is checkable on its own terms, by the function a receiver uses.
        assertTrue(
            GroupKeyStateEvent.isSignedByGroup(
                event = signed,
                thresholdPublicKey = thresholdPublicKey,
                path = SharedKeyDerivation.MARMOT_ADMIN_GROUP_PATH
            )
        )
    }

    @Test
    fun `the third member picks up the state without having signed it`() = runBlocking {
        val creator = device(members[0], signerIndex = 0)
        val other = device(members[1], signerIndex = 1)
        val absent = device(members[2], signerIndex = 2)

        val session = GroupKeyStateManager.propose(
            database = creator.db,
            localChatRoom = creator.room,
            userPublicKey = creator.publicKey,
            key = ceremonyOn(creator)
        )
        pump(creator, other, absent)

        // Only two of the three approve, which is the point of a 2-of-3 key.
        FrostSigningManager.approve(other.db, other.room, session.id)
        pump(creator, other, absent)

        assertNull(
            absent.session(session.id)?.signApprovedAt,
            "the third member must not have been made to sign"
        )
        assertNotNull(
            absent.keyState(),
            "a member the quorum did not need still learns what the room signs with"
        )
        assertEquals(adminRoomId, absent.keyState()?.announcedBy)
    }

    // ---- What a room signs as, once it has a key state ---------------------

    @Test
    fun `a dialect the group signs is authored by the room it was signed in`() = runBlocking {
        val creator = device(members[0], signerIndex = 0)
        val other = device(members[1], signerIndex = 1)

        val template = DialectEvent.build(name = "Sepedi", country = "ZA", language = "nso")
        val session = FrostSigningManager.proposeSigning(
            database = creator.db,
            localChatRoom = creator.room,
            userPublicKey = creator.publicKey,
            kind = template.kind,
            tags = template.tags,
            content = template.content
        )
        pump(creator, other)
        FrostSigningManager.approve(other.db, other.room, session.id)
        pump(creator, other)

        listOf(creator, other).forEach { device ->
            val dialect = device.db.mantraDialectDao().getDialectById(session.eventId)
            assertNotNull(dialect, "a signed dialect should exist on every signer's device")
            assertEquals("Sepedi", dialect.name)
            // The ask this whole change serves: the group's work is authored by
            // the group's room, not by the member who typed it and not by the
            // bare threshold key.
            assertEquals(adminRoomId, dialect.publicKey)
            assertTrue(dialect.publicKey != creator.publicKey)
            assertTrue(dialect.publicKey != rootPublicKey)
            assertTrue(
                Nip01Crypto.verify(
                    signature = dialect.signature.hexToByteArray(),
                    hash = dialect.id.hexToByteArray(),
                    pubKey = adminRoomId.hexToByteArray()
                )
            )
        }
    }

    @Test
    fun `a proposer cannot choose the key the group signs as`() = runBlocking {
        val other = device(members[1], signerIndex = 1)

        // The one thing choosing the path would buy an attacker: a proposal for
        // a perfectly true key state, re-authored under the group's root key
        // rather than the room's. Every field a signer is shown is honest; the
        // author is not the one this room answers to.
        val createdAt = 1_700_000_000L
        val tags = GroupKeyStateEvent.assembleTags(
            chatRoomId = adminRoomId,
            dkgSessionId = ceremonyId,
            path = SharedKeyDerivation.MARMOT_ADMIN_GROUP_PATH
        )
        val forged = Event(
            id = EventHasher.hashId(
                pubKey = rootPublicKey,
                createdAt = createdAt,
                kind = GroupKeyStateEvent.KIND,
                tags = tags,
                content = thresholdPublicKey
            ),
            pubKey = rootPublicKey,
            createdAt = createdAt,
            kind = GroupKeyStateEvent.KIND,
            tags = tags,
            content = thresholdPublicKey,
            sig = ""
        )

        val sessionId = "5".repeat(64)
        FrostSigningManager.processSigningPayload(
            database = other.db,
            localChatRoom = other.room,
            innerEvent = Event(
                id = "6".repeat(64),
                pubKey = members[0],
                createdAt = createdAt,
                kind = FrostSigningEvents.PROPOSAL,
                tags = FrostSigningEvents.assembleTags(
                    sessionId = sessionId,
                    dkgSessionId = ceremonyId
                ),
                content = forged.toJson(),
                sig = ""
            ),
            userPublicKey = other.publicKey
        )

        // The author is rebuilt from this device's own reading of the room, so
        // the id does not come out where the proposal says it should and no
        // session opens at all. Nothing of this member's is published, and there
        // is nothing to approve.
        assertNull(
            other.session(sessionId),
            "a proposal authored under a key the room did not derive must not open a session"
        )
    }

    @Test
    fun `a true announcement nobody signed no longer becomes a state`() = runBlocking {
        val member = device(members[0], signerIndex = 0)

        // Exactly what a member used to be able to say on their own, and exactly
        // what this room signs with: true, well formed, addressed correctly, and
        // agreed to by nobody. That is now the whole reason it is refused.
        val announcement = Event(
            id = "7".repeat(64),
            pubKey = member.publicKey,
            createdAt = 1_700_000_000L,
            kind = GroupKeyStateEvent.KIND,
            tags = GroupKeyStateEvent.assembleTags(
                chatRoomId = adminRoomId,
                dkgSessionId = ceremonyId,
                path = SharedKeyDerivation.MARMOT_ADMIN_GROUP_PATH
            ),
            content = thresholdPublicKey,
            sig = ""
        )

        assertNull(GroupKeyStateManager.record(member.db, adminRoomId, announcement))
        assertNull(member.keyState(), "an unsigned announcement must leave no row behind")
    }

    @Test
    fun `a room derived off the default path signs at its own`() = runBlocking {
        // The reason a path is resolved and checked rather than assumed. This
        // room is real, derived, and nowhere near the constant -- a session that
        // hardcoded MARMOT_ADMIN_GROUP_PATH would sign it as the wrong key and
        // every signature would verify against nothing.
        val sibling = listOf(9420L, 0L, 1L)
        val siblingRoomId = SharedKeyDerivation.marmotGroupId(thresholdPublicKey, sibling)

        val creator = device(
            publicKey = members[0],
            signerIndex = 0,
            roomId = siblingRoomId,
            description = SharedKeyDerivation.describe("A second room.", sibling)
        )
        val other = device(
            publicKey = members[1],
            signerIndex = 1,
            roomId = siblingRoomId,
            description = SharedKeyDerivation.describe("A second room.", sibling)
        )

        val session = GroupKeyStateManager.propose(
            database = creator.db,
            localChatRoom = creator.room,
            userPublicKey = creator.publicKey,
            key = ceremonyOn(creator),
            path = sibling
        )

        assertEquals(SharedKeyDerivation.formatPath(sibling), session.derivationPath)
        assertEquals(siblingRoomId, Event.fromJson(session.unsignedEventJson).pubKey)

        pump(creator, other)
        FrostSigningManager.approve(other.db, other.room, session.id)
        pump(creator, other)

        val state = creator.keyState()
        assertNotNull(state, "a room off the default path must still reach a signed state")
        assertEquals(SharedKeyDerivation.formatPath(sibling), state.derivationPath)
        assertEquals(siblingRoomId, state.announcedBy)
        assertTrue(state.verifies())
    }

    @Test
    fun `a room that is not derived from the key signs as the key itself`() = runBlocking {
        // The fallback kept for rooms the app no longer makes: the ceremony ran
        // in this very room, so the room's id is a random 32 bytes rather than
        // anything walked to. There is no room key to sign as, so it signs as the
        // group's -- which is what it did before any of this.
        val undeerived = "d".repeat(64)
        val creator = device(
            publicKey = members[0],
            signerIndex = 0,
            roomId = undeerived,
            description = null
        )

        val template = DialectEvent.build(name = "Sepedi", country = "ZA", language = "nso")
        val session = FrostSigningManager.proposeSigning(
            database = creator.db,
            localChatRoom = creator.room,
            userPublicKey = creator.publicKey,
            kind = template.kind,
            tags = template.tags,
            content = template.content
        )

        assertNull(session.derivationPath, "no path reaches a room that was not derived")
        assertEquals(emptyList(), session.pathIndices())
        assertEquals(rootPublicKey, Event.fromJson(session.unsignedEventJson).pubKey)
    }
}
