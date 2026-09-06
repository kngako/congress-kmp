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
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import press.mantra.compose.database.MantraDatabase
import press.mantra.compose.database.model.ChatMessage
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

        /** The events a session signs, in the order its proposal fixed. */
        suspend fun items(sessionId: String) =
            db.frostSigningSessionDao().getItems(sessionId)

        /** The one event a session of one signs. */
        suspend fun item(sessionId: String) = items(sessionId).single()

        suspend fun keyState() = db.groupKeyStateDao().getByChatRoomId(roomId)

        /** Everything the group has signed here, as the group signed it. */
        suspend fun signedEvents() = db.groupSignedEventDao().getByChatRoomId(roomId)
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
        assertEquals(creator.item(session.id).eventId, payload.id)
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

        val payload = Event.fromJson(creator.item(session.id).unsignedEventJson)

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
        assertEquals(creator.item(session.id).eventId, other.item(session.id).eventId)
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

        assertTrue(
            FrostSigningManager.isAwaitingApproval(
                other.session(session.id)!!,
                other.items(session.id)
            )
        )
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

        val signed = FrostSigningManager.signedEvent(creator.item(session.id))
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

    // ---- Signing several events in one session -----------------------------

    /**
     * The batch, end to end, between two devices over two databases: three
     * events, one signer set, one approval, and three signatures that verify
     * against the room.
     *
     * Everything about batching that could be wrong and still compile is wrong
     * here or nowhere -- a shared nonce, a mismatched order, a payload split the
     * wrong way. A signature that verifies is the only evidence any of it is
     * wired up correctly, and there are three of them to disagree.
     */
    @Test
    fun `a batch of three is signed in one session`() = runBlocking {
        val creator = device(members[0], signerIndex = 0)
        val other = device(members[1], signerIndex = 1)

        val session = FrostSigningManager.proposeSigningBatch(
            database = creator.db,
            localChatRoom = creator.room,
            userPublicKey = creator.publicKey,
            events = dialects()
        )
        pump(creator, other)
        FrostSigningManager.approve(other.db, other.room, session.id)
        pump(creator, other)

        listOf(creator, other).forEach { device ->
            val items = device.items(session.id)

            assertEquals(3, items.size, "every device signs the whole batch")
            assertEquals(FrostSigningStage.COMPLETE, device.session(session.id)?.stage)

            items.forEach { item ->
                assertTrue(
                    Nip01Crypto.verify(
                        signature = assertNotNull(item.signature).hexToByteArray(),
                        hash = item.eventId.hexToByteArray(),
                        pubKey = adminRoomId.hexToByteArray()
                    ),
                    "item ${item.itemIndex} must verify against the room it was signed in"
                )
            }
        }
    }

    /**
     * What the group signed, kept as the group signed it.
     *
     * Every other assertion in this file reads a session's own rows, which exist
     * only on a device that ran the session and are swept away with it. This
     * reads the record that outlives it -- and reads it back through
     * `verifies()`, so a column that failed to round-trip fails here rather than
     * years later when somebody tries to hand the batch on in an archive.
     *
     * The path is the part no device is told. Each resolves it from the room it
     * is standing in, so both arriving at `m/9420/0/0` is two independent
     * derivations agreeing rather than one value being copied about.
     */
    @Test
    fun `a signed batch is kept as events, with the path it was signed at`() = runBlocking {
        val creator = device(members[0], signerIndex = 0)
        val other = device(members[1], signerIndex = 1)

        val session = FrostSigningManager.proposeSigningBatch(
            database = creator.db,
            localChatRoom = creator.room,
            userPublicKey = creator.publicKey,
            events = dialects()
        )
        pump(creator, other)
        FrostSigningManager.approve(other.db, other.room, session.id)
        pump(creator, other)

        listOf(creator, other).forEach { device ->
            val signed = device.signedEvents()

            assertEquals(
                device.items(session.id).map { it.eventId }.toSet(),
                signed.map { it.id }.toSet(),
                "every event the session signed should be on file as the group made it"
            )

            signed.forEach { event ->
                assertEquals(adminRoomId, event.publicKey)
                assertEquals(SharedKeyDerivation.formatPath(), event.derivationPath)
                assertEquals(
                    SharedKeyDerivation.MARMOT_ADMIN_GROUP_PATH,
                    event.pathIndices()
                )
                assertEquals(session.id, event.frostSigningSessionId)
                assertTrue(
                    event.verifies(),
                    "${event.id} does not read back as something this room signed"
                )
            }
        }
    }

    /**
     * The one that catches the mistake this whole design exists to prevent.
     *
     * Every positive test above still passes if two items share a nonce -- the
     * signatures verify perfectly well. What sharing costs is the secret share,
     * to anyone who sees both partial signatures. So the aggregates and the seeds
     * are asserted pairwise distinct, which is cheap and is the only assertion
     * here that an index bug cannot slip past.
     */
    @Test
    fun `no two events in a batch share a nonce`() = runBlocking {
        val creator = device(members[0], signerIndex = 0)
        val other = device(members[1], signerIndex = 1)

        val session = FrostSigningManager.proposeSigningBatch(
            database = creator.db,
            localChatRoom = creator.room,
            userPublicKey = creator.publicKey,
            events = dialects()
        )
        pump(creator, other)
        FrostSigningManager.approve(other.db, other.room, session.id)
        pump(creator, other)

        listOf(creator, other).forEach { device ->
            val items = device.items(session.id)

            assertEquals(3, items.mapNotNull { it.aggregatedNonce }.toSet().size)
            assertEquals(3, items.map { it.nonceRandom }.toSet().size)
            assertEquals(3, items.map { it.eventId }.toSet().size)
        }

        // A seed is this device's own, so the two devices must not have arrived
        // at the same ones either.
        assertEquals(
            emptySet(),
            creator.items(session.id).map { it.nonceRandom }.toSet()
                .intersect(other.items(session.id).map { it.nonceRandom }.toSet())
        )
    }

    /** Four messages for three events, which is the whole point of batching. */
    @Test
    fun `a batch costs one round of messages, not one per event`() = runBlocking {
        val creator = device(members[0], signerIndex = 0)
        val other = device(members[1], signerIndex = 1)

        val session = FrostSigningManager.proposeSigningBatch(
            database = creator.db,
            localChatRoom = creator.room,
            userPublicKey = creator.publicKey,
            events = dialects()
        )
        pump(creator, other)
        FrostSigningManager.approve(other.db, other.room, session.id)
        pump(creator, other)

        // Proposal, nonce, signer set, partial signature, signature.
        assertEquals(
            listOf(
                FrostSigningEvents.PROPOSAL,
                FrostSigningEvents.NONCE,
                FrostSigningEvents.SIGNER_SET,
                FrostSigningEvents.PARTIAL_SIGNATURE,
                FrostSigningEvents.SIGNATURE,
            ),
            outbox(creator).map { it.kind }
        )

        // The other member is a signer and not the coordinator: one nonce and one
        // partial signature, each carrying all three values.
        assertEquals(
            listOf(FrostSigningEvents.NONCE, FrostSigningEvents.PARTIAL_SIGNATURE),
            outbox(other).map { it.kind }
        )
        outbox(other).forEach { queued ->
            assertEquals(3, queued.content.split(",").size, "kind ${queued.kind} must carry three values")
        }

        // And one question put to the member, not three.
        assertEquals(
            1,
            other.db.chatMessageDao().getChatMessagesByChatRoomId(other.roomId)
                .count { it.chatMessage.messageType == ChatMessage.TYPE_FROST_APPROVAL_NEEDED }
        )
    }

    /** Every event of a batch is applied, not just the first. */
    @Test
    fun `every dialect in a batch lands on every device`() = runBlocking {
        val creator = device(members[0], signerIndex = 0)
        val other = device(members[1], signerIndex = 1)

        val session = FrostSigningManager.proposeSigningBatch(
            database = creator.db,
            localChatRoom = creator.room,
            userPublicKey = creator.publicKey,
            events = dialects()
        )
        pump(creator, other)
        FrostSigningManager.approve(other.db, other.room, session.id)
        pump(creator, other)

        listOf(creator, other).forEach { device ->
            val names = device.items(session.id).map { item ->
                assertNotNull(
                    device.db.mantraDialectDao().getDialectById(item.eventId),
                    "item ${item.itemIndex} should have been applied"
                ).name
            }

            assertEquals(listOf("Sepedi", "isiZulu", "Setswana"), names)
        }
    }

    /**
     * A proposal is the one place a remote party decides how much work everybody
     * else does, so the size cap is checked on arrival and not only when
     * proposing.
     */
    @Test
    fun `a batch larger than the cap is refused when proposed`() = runBlocking {
        val creator = device(members[0], signerIndex = 0)

        val tooMany = (0..FrostSigningManager.MAX_BATCH_SIZE).map { index ->
            DialectEvent.build(name = "d$index", country = "ZA", language = "l$index")
        }

        assertFailsWith<IllegalArgumentException> {
            FrostSigningManager.proposeSigningBatch(
                database = creator.db,
                localChatRoom = creator.room,
                userPublicKey = creator.publicKey,
                events = tooMany
            )
        }

        Unit
    }

    /**
     * A payload that is not the batch's length is left out, not truncated -- and
     * leaving it out stalls the session rather than poisoning it.
     *
     * The length check is the only thing standing between a batch and a signer
     * whose contribution lines up against the wrong messages. Truncating a long
     * payload or padding a short one would produce partial signatures aggregated
     * against events nobody agreed to.
     */
    @Test
    fun `a nonce payload of the wrong length is left out rather than truncated`() = runBlocking {
        val creator = device(members[0], signerIndex = 0)
        val other = device(members[1], signerIndex = 1)

        val session = FrostSigningManager.proposeSigningBatch(
            database = creator.db,
            localChatRoom = creator.room,
            userPublicKey = creator.publicKey,
            events = dialects()
        )
        pump(creator, other)

        // One nonce offered for a batch of three, from a member the coordinator
        // would otherwise have picked.
        FrostSigningManager.processSigningPayload(
            database = creator.db,
            localChatRoom = creator.room,
            innerEvent = Event(
                id = "1".repeat(64),
                pubKey = other.publicKey,
                createdAt = 1,
                kind = FrostSigningEvents.NONCE,
                tags = FrostSigningEvents.assembleTags(session.id),
                content = "aa".repeat(66),
                sig = ""
            ),
            userPublicKey = creator.publicKey
        )

        assertNull(
            creator.session(session.id)?.signerIds,
            "a payload of the wrong length must not count towards the threshold"
        )

        // And it is a stall, not damage: the real nonce replaces it and the batch
        // finishes. That is the composite key on FrostSignerMessage doing its job.
        FrostSigningManager.approve(other.db, other.room, session.id)
        pump(creator, other)

        assertEquals(FrostSigningStage.COMPLETE, creator.session(session.id)?.stage)
        assertEquals(3, creator.items(session.id).count { it.signature != null })
    }

    /**
     * A second proposal under a session's own id cannot change what it signs.
     *
     * The rule the whole design rests on. Every item's nonce seed is already
     * committed to that item's message; giving the session a different batch
     * would have those seeds produce a second partial signature over a second
     * message, which is how a secret share is extracted.
     */
    @Test
    fun `a second proposal under the same id cannot change what a session signs`() = runBlocking {
        val creator = device(members[0], signerIndex = 0)
        val other = device(members[1], signerIndex = 1)

        val session = FrostSigningManager.proposeSigningBatch(
            database = creator.db,
            localChatRoom = creator.room,
            userPublicKey = creator.publicKey,
            events = dialects()
        )
        pump(creator, other)

        val before = other.items(session.id)

        val substitute = DialectEvent.build(name = "Xitsonga", country = "ZA", language = "tso")
        FrostSigningManager.processSigningPayload(
            database = other.db,
            localChatRoom = other.room,
            innerEvent = Event(
                id = "2".repeat(64),
                pubKey = creator.publicKey,
                createdAt = 1,
                kind = FrostSigningEvents.PROPOSAL,
                tags = FrostSigningEvents.assembleTags(session.id, dkgSessionId = ceremonyId),
                content = FrostSigningEvents.encodeProposal(
                    listOf(
                        Event(
                            id = "3".repeat(64),
                            pubKey = adminRoomId,
                            createdAt = substitute.createdAt,
                            kind = substitute.kind,
                            tags = substitute.tags,
                            content = substitute.content,
                            sig = ""
                        )
                    )
                ),
                sig = ""
            ),
            userPublicKey = other.publicKey
        )

        assertEquals(
            before.map { it.eventId },
            other.items(session.id).map { it.eventId },
            "a re-proposal must be ignored, not applied"
        )
        assertEquals(before.map { it.nonceRandom }, other.items(session.id).map { it.nonceRandom })
    }

    /** Three dialects, distinct enough that an order bug shows up as a wrong name. */
    private fun dialects() = listOf(
        Triple("Sepedi", "ZA", "nso"),
        Triple("isiZulu", "ZA", "zul"),
        Triple("Setswana", "ZA", "tsn"),
    ).map { (name, country, language) ->
        DialectEvent.build(name = name, country = country, language = language)
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
            val dialect = device.db.mantraDialectDao().getDialectById(device.item(session.id).eventId)
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
        assertEquals(siblingRoomId, Event.fromJson(creator.item(session.id).unsignedEventJson).pubKey)

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
        assertEquals(rootPublicKey, Event.fromJson(creator.item(session.id).unsignedEventJson).pubKey)
    }
}
