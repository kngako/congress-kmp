package press.mantra.compose.database.dao

import androidx.room3.Room
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import kotlinx.coroutines.runBlocking
import press.mantra.compose.database.MantraDatabase
import press.mantra.compose.database.builder.getRoomDatabase
import press.mantra.compose.database.model.ChatRoom
import press.mantra.compose.database.model.FrostSignerMessage
import press.mantra.compose.database.model.FrostSigningSession
import press.mantra.compose.database.model.NostrEvent
import press.mantra.compose.database.model.Profile
import press.mantra.compose.database.model.types.FrostSigningStage
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * The signing counterpart to [DkgSessionDaoJvmTest], and it differs from the DKG in one way
 * that shapes every query here: "unlike a DKG a group signs repeatedly, so there is no single
 * current one to observe". Sessions accumulate, which makes room scoping and ordering
 * load-bearing rather than incidental.
 *
 * The duplicate-suppression property is the same and matters for the same reason. The composite
 * key (sessionId, signerPublicKey, kind) is what makes a redelivered nonce or partial signature
 * replace its predecessor instead of adding a row, and `countMessagesByKind` is what decides
 * that enough signers have answered. A second row for one signer would let a session cross its
 * threshold while short a real participant.
 */
class FrostSigningSessionDaoJvmTest {

    private val db: MantraDatabase = getRoomDatabase(
        Room.inMemoryDatabaseBuilder<MantraDatabase>()
    )

    @AfterTest
    fun closeDb() = db.close()

    private val user = KeyPair().pubKey.toHexKey()
    private val alice = KeyPair().pubKey.toHexKey()
    private val bob = KeyPair().pubKey.toHexKey()
    private val roomOne = "11".repeat(32)
    private val roomTwo = "22".repeat(32)

    private val nonceKind = 1
    private val partialSignatureKind = 2

    private suspend fun seedRooms() {
        val nostrEventId = "c".repeat(64)
        db.nostrEventDao().upsert(
            NostrEvent(
                id = nostrEventId,
                pubKey = user,
                kind = 0,
                tags = emptyArray(),
                content = "{}",
                sig = "0".repeat(128),
            )
        )
        db.profileDao().upsert(Profile(publicKey = user, userName = "user", nostrEventId = nostrEventId))
        listOf(roomOne, roomTwo).forEach { id ->
            db.chatRoomDao().upsert(
                ChatRoom(
                    id = id,
                    userPublicKey = user,
                    subject = null,
                    description = null,
                    mlsGroupState = null,
                )
            )
        }
    }

    private suspend fun session(
        id: String,
        chatRoomId: String = roomOne,
        createdAt: Instant = Instant.fromEpochSeconds(1_000),
        stage: FrostSigningStage = FrostSigningStage.COLLECTING_NONCES,
        signature: String? = null,
    ): FrostSigningSession = FrostSigningSession(
        id = id,
        chatRoomId = chatRoomId,
        coordinatorPublicKey = user,
        userPublicKey = user,
        dkgSessionId = "dkg-1",
        threshold = 2,
        participantCount = 3,
        signerId = 1,
        stage = stage,
        unsignedEventJson = "{}",
        eventId = "ff".repeat(32),
        nonceRandom = "aa".repeat(32),
        signature = signature,
        createdAt = createdAt,
    ).also { db.frostSigningSessionDao().upsert(it) }

    private suspend fun message(
        sessionId: String,
        signer: String,
        kind: Int = nonceKind,
        payload: String = "aa".repeat(32),
        createdAt: Instant = Instant.fromEpochSeconds(1_000),
    ) = db.frostSigningSessionDao().upsert(
        FrostSignerMessage(
            sessionId = sessionId,
            signerPublicKey = signer,
            kind = kind,
            payload = payload,
            createdAt = createdAt,
        )
    )

    @Test
    fun `a signing session reads back by its id`() = runBlocking {
        seedRooms()
        session("s1", stage = FrostSigningStage.COLLECTING_PARTIAL_SIGNATURES)

        val found = assertNotNull(db.frostSigningSessionDao().getSessionById("s1"))

        assertEquals(FrostSigningStage.COLLECTING_PARTIAL_SIGNATURES, found.stage)
        assertNull(db.frostSigningSessionDao().getSessionById("nope"))
    }

    /**
     * Sessions accumulate rather than replacing each other, so a room keeps a history and the
     * newest is the one a resume cares about.
     */
    @Test
    fun `a rooms signing sessions accumulate newest first`() = runBlocking {
        seedRooms()
        session("first", createdAt = Instant.fromEpochSeconds(1_000))
        session("second", createdAt = Instant.fromEpochSeconds(2_000))
        session("third", createdAt = Instant.fromEpochSeconds(3_000))

        assertEquals(
            listOf("third", "second", "first"),
            db.frostSigningSessionDao().getSessionsForChatRoom(roomOne).map { it.id },
        )
        assertEquals("third", db.frostSigningSessionDao().getLatestSessionForChatRoom(roomOne)?.id)
    }

    /**
     * Signing happens in the #admins room, and a device can be in more than one. A session from
     * another room appearing here would have a signer answering a request its group never made.
     */
    @Test
    fun `sessions are scoped to their own room`() = runBlocking {
        seedRooms()
        session("mine", chatRoomId = roomOne)
        session("theirs", chatRoomId = roomTwo, createdAt = Instant.fromEpochSeconds(9_000))

        assertEquals(listOf("mine"), db.frostSigningSessionDao().getSessionsForChatRoom(roomOne).map { it.id })
        assertEquals("mine", db.frostSigningSessionDao().getLatestSessionForChatRoom(roomOne)?.id)
        assertEquals(emptyList(), db.frostSigningSessionDao().getSessionsForChatRoom("33".repeat(32)))
    }

    /**
     * The threshold gate. A redelivered nonce must replace the signer's earlier one rather than
     * add a row, or the count crosses the threshold with fewer signers than the session
     * requires -- and the aggregation proceeds on a set that was never assembled.
     */
    @Test
    fun `a resent nonce replaces the signers earlier one`() = runBlocking {
        seedRooms()
        session("s1")
        message("s1", alice, payload = "aa".repeat(32))

        message("s1", alice, payload = "bb".repeat(32))

        assertEquals(1, db.frostSigningSessionDao().countMessagesByKind("s1", nonceKind))
        assertEquals(
            "bb".repeat(32),
            db.frostSigningSessionDao().getMessage("s1", nonceKind, alice)?.payload,
        )
    }

    /**
     * A signer contributes to both rounds of a session -- a nonce and then a partial signature
     * -- and the kind in the composite key is what keeps the second from overwriting the first.
     */
    @Test
    fun `a signer holds a nonce and a partial signature at once`() = runBlocking {
        seedRooms()
        session("s1")
        message("s1", alice, kind = nonceKind, payload = "aa".repeat(32))
        message("s1", alice, kind = partialSignatureKind, payload = "cc".repeat(32))

        assertEquals(1, db.frostSigningSessionDao().countMessagesByKind("s1", nonceKind))
        assertEquals(1, db.frostSigningSessionDao().countMessagesByKind("s1", partialSignatureKind))
        assertEquals(
            "aa".repeat(32),
            db.frostSigningSessionDao().getMessage("s1", nonceKind, alice)?.payload,
            "the partial signature overwrote the nonce",
        )
    }

    @Test
    fun `messages are counted per session and per round`() = runBlocking {
        seedRooms()
        session("s1")
        session("s2")
        message("s1", alice, kind = nonceKind)
        message("s1", bob, kind = nonceKind)
        message("s2", alice, kind = nonceKind)

        assertEquals(2, db.frostSigningSessionDao().countMessagesByKind("s1", nonceKind))
        assertEquals(1, db.frostSigningSessionDao().countMessagesByKind("s2", nonceKind))
        assertEquals(0, db.frostSigningSessionDao().countMessagesByKind("s1", partialSignatureKind))
        assertNull(db.frostSigningSessionDao().getMessage("s1", nonceKind, user))
    }

    /**
     * Worth recording because it is the one place these two DAOs disagree: FROST orders a
     * round's messages by `createdAt`, where the DKG orders the same query by participant
     * public key. Arrival order is per-device, so this ordering is not canonical across the
     * group the way the DKG's is. Pinned as it stands rather than assumed to be either
     * deliberate or a slip -- a caller that needs a canonical signer order has to impose one.
     */
    @Test
    fun `a rounds messages are ordered by arrival, unlike the dkg`() = runBlocking {
        seedRooms()
        session("s1")
        val byKey = listOf(alice, bob).sorted()
        message("s1", byKey[1], createdAt = Instant.fromEpochSeconds(1_000))
        message("s1", byKey[0], createdAt = Instant.fromEpochSeconds(2_000))

        val found = db.frostSigningSessionDao().getMessagesByKind("s1", nonceKind).map { it.signerPublicKey }

        assertEquals(listOf(byKey[1], byKey[0]), found, "FROST returns a round in arrival order")
    }

    /** A finished session keeps its signature, which is what a resume reads to avoid re-signing. */
    @Test
    fun `a completed session keeps its signature`() = runBlocking {
        seedRooms()
        session("s1", stage = FrostSigningStage.COMPLETE, signature = "ab".repeat(32))

        val found = assertNotNull(db.frostSigningSessionDao().getSessionById("s1"))

        assertEquals(FrostSigningStage.COMPLETE, found.stage)
        assertEquals("ab".repeat(32), found.signature)
    }
}
