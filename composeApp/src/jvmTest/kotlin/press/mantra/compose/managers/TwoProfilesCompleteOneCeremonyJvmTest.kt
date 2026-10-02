package press.mantra.compose.managers

import androidx.room3.Room
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import kotlinx.coroutines.runBlocking
import press.mantra.compose.database.MantraDatabase
import press.mantra.compose.database.builder.getRoomDatabase
import press.mantra.compose.database.model.DkgSession
import press.mantra.compose.database.model.NostrEvent
import press.mantra.compose.database.model.Profile
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.database.model.types.DkgRitualStage
import press.mantra.compose.nostr.dkg.DkgRitualEvents
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A key ceremony run to the end, with two of its three members being two profiles on
 * one device.
 *
 * This is the state everything else about the per-profile keying exists to protect,
 * and the only way to see it is to finish a ceremony. A ChillDKG hashes the host keys
 * and `n` into its own session identity, so a device contributing one host key where
 * the group counts two does not get a weaker key — it gets no key. Sharing one
 * `DkgSession` row between two profiles was exactly that: one host key, one piece of
 * randomness, one share, for two members.
 *
 * Measured, by pointing `acceptProposal` back at a row of any profile's and watching:
 * both tests here fail, and they fail *earlier* than expected. The second profile
 * adopts the first's row, finds a host key already published on it, and so never
 * publishes one of its own — leaving the ceremony two host keys short of the three it
 * counts, for good. It does not produce a bad key, it stalls at the first round and
 * stays there. "No row for bob" is the first assertion to go.
 *
 * So what is asserted is the outcome rather than the plumbing: three participants,
 * three distinct shares, one threshold key they all agree on, and the two profiles on
 * this device holding a different share each.
 *
 * ### What is real here and what is not
 *
 * The protocol is real — `ChillDkgRitualManager` drives genuine ChillDKG through the
 * bundled secp256k1, and the shares at the end are shares. What is simulated is the
 * relays: [pump] moves each device's queued payloads into the others rather than
 * encrypting and sending them, and approvals are given as soon as a step asks for
 * them. That is the right seam. `ChillDkgRitualOrderingTest` already covers the
 * protocol's ordering rule directly, and the transports have their own tests; what is
 * uncovered until here is the manager completing a ceremony at all, which nothing else
 * in the suite does.
 *
 * Alice and Bob share one database, as two profiles on a device do. Carol gets her
 * own, so the ceremony has a participant that is genuinely elsewhere and the room has
 * to be derived to the same id on both sides rather than copied.
 */
class TwoProfilesCompleteOneCeremonyJvmTest {

    /** The device holding two profiles. */
    private val db: MantraDatabase = getRoomDatabase(
        Room.inMemoryDatabaseBuilder<MantraDatabase>()
    )

    /** Somebody else's. */
    private val carolDb: MantraDatabase = getRoomDatabase(
        Room.inMemoryDatabaseBuilder<MantraDatabase>()
    )

    @AfterTest
    fun closeDb() {
        db.close()
        carolDb.close()
    }

    private val aliceKeys = KeyPair()
    private val bobKeys = KeyPair()
    private val carolKeys = KeyPair()

    private val alice = aliceKeys.pubKey.toHexKey()
    private val bob = bobKeys.pubKey.toHexKey()
    private val carol = carolKeys.pubKey.toHexKey()

    private val quorum = 2

    /**
     * One participant, as the manager sees it: whose profile, in which database, with
     * which room and which secret.
     */
    private data class Member(
        val name: String,
        val publicKey: HexKey,
        val privateKey: ByteArray,
        val database: MantraDatabase,
        val room: LocalChatRoom,
    )

    private lateinit var members: List<Member>
    private lateinit var ceremonyId: String

    /** Payloads already handed to a profile, so the pump does not re-feed the backlog. */
    private val delivered = mutableSetOf<Pair<HexKey, String>>()

    private suspend fun seedProfile(database: MantraDatabase, publicKey: HexKey) {
        val nostrEventId = publicKey.take(63) + "f"
        database.nostrEventDao().upsert(
            NostrEvent(
                id = nostrEventId,
                pubKey = publicKey,
                kind = 0,
                tags = emptyArray(),
                content = "{}",
                sig = "0".repeat(128),
            )
        )
        database.profileDao().upsert(
            Profile(publicKey = publicKey, userName = publicKey.take(6), nostrEventId = nostrEventId)
        )
    }

    /**
     * The group's room, stood up on both devices.
     *
     * Derived rather than copied: a NIP-17 room's id is an aggregate of its member
     * keys, so each device building it from its own point of view has to land on the
     * same id. If that stopped being true the ceremony would run in two rooms and
     * nothing would reach anybody, which is worth having the test depend on.
     */
    private suspend fun openGroup() {
        listOf(alice, bob, carol).forEach {
            seedProfile(db, it)
            seedProfile(carolDb, it)
        }

        val onThisDevice = assertNotNull(
            db.nostrNip17Dao().createNip17ChatRoom(
                userPublicKey = alice,
                participantPublicKeys = listOf(bob, carol),
                subject = "Two personas, one key",
            )
        )
        val onCarols = assertNotNull(
            carolDb.nostrNip17Dao().createNip17ChatRoom(
                userPublicKey = carol,
                participantPublicKeys = listOf(alice, bob),
                subject = "Two personas, one key",
            )
        )
        assertEquals(
            onThisDevice.chatRoom.id,
            onCarols.chatRoom.id,
            "both devices have to derive the same room or the ceremony runs in two",
        )

        members = listOf(
            Member("alice", alice, aliceKeys.privKey!!, db, onThisDevice),
            Member("bob", bob, bobKeys.privKey!!, db, onThisDevice),
            Member("carol", carol, carolKeys.privKey!!, carolDb, onCarols),
        )
    }

    /** A queued payload as the rumor its sender wrote, which is what the manager takes. */
    private suspend fun outbox(database: MantraDatabase, roomId: String): List<Event> =
        database.giftWrapPayloadDao()
            .getByChatRoomAndKinds(roomId, DkgRitualEvents.ALL.toList())
            .map {
                Event(
                    id = it.id,
                    pubKey = it.publicKey,
                    createdAt = it.createdAt.epochSeconds,
                    kind = it.kind,
                    tags = it.tags,
                    content = it.content,
                    sig = "",
                )
            }

    /**
     * One turn of the relays: every payload any device has queued is handed to every
     * profile that has not seen it, and then each is asked whether it is waiting on its
     * owner and told yes.
     *
     * Returns whether anything moved, so the caller can stop when the ceremony has
     * either finished or stalled rather than looping a fixed number of times.
     */
    private suspend fun pump(): Boolean {
        var moved = false

        val queued = members
            .distinctBy { it.database }
            .flatMap { outbox(it.database, it.room.chatRoom.id) }
            .distinctBy { it.id }

        members.forEach { member ->
            queued.forEach { event ->
                if (delivered.add(member.publicKey to event.id)) {
                    moved = true
                    ChillDkgRitualManager.processRitualPayload(
                        database = member.database,
                        localChatRoom = member.room,
                        innerEvent = event,
                        userPublicKey = member.publicKey,
                        nostrPrivateKey = member.privateKey,
                    )
                }
            }
        }

        members.forEach { member ->
            val session = sessionOf(member) ?: return@forEach
            val step = ChillDkgRitualManager.pendingApproval(member.database, session) ?: return@forEach

            moved = true
            ChillDkgRitualManager.approve(
                database = member.database,
                localChatRoom = member.room,
                sessionId = session.id,
                userPublicKey = member.publicKey,
                step = step,
                nostrPrivateKey = member.privateKey,
            )
        }

        return moved
    }

    private suspend fun sessionOf(member: Member): DkgSession? =
        member.database.dkgSessionDao().getSessionById(ceremonyId, member.publicKey)

    /** Runs the ceremony until nothing more moves, or [limit] turns have passed. */
    private suspend fun runToCompletion(limit: Int = 24) {
        repeat(limit) {
            if (!pump()) return
            if (members.all { sessionOf(it)?.stage == DkgRitualStage.COMPLETE }) return
        }
    }

    @Test
    fun `two profiles on one device each come out of one ceremony with their own share`(): Unit =
        runBlocking {
            openGroup()

            // Alice opens it, which is also her agreeing to be in it.
            ceremonyId = ChillDkgRitualManager.proposeRitual(
                database = db,
                localChatRoom = members[0].room,
                userPublicKey = alice,
                nostrPrivateKey = aliceKeys.privKey!!,
                threshold = quorum,
            ).id

            runToCompletion()

            val finished = members.associateWith { assertNotNull(sessionOf(it), "no row for ${it.name}") }

            finished.forEach { (member, session) ->
                assertEquals(
                    DkgRitualStage.COMPLETE,
                    session.stage,
                    "${member.name} did not finish: ${session.stage}, ${session.failureReason}",
                )
                assertNotNull(session.secretShare, "${member.name} finished without a share")
            }

            // The whole point: one key, agreed by everybody.
            val keys = finished.values.map { assertNotNull(it.thresholdPublicKey) }.distinct()
            assertEquals(
                1,
                keys.size,
                "three participants, one threshold key -- got ${keys.size}",
            )

            // And three shares of it, not one shared three ways.
            val shares = finished.values.map { it.secretShare }
            assertEquals(
                3,
                shares.distinct().size,
                "each participant holds its own share, including the two profiles here",
            )

            val alices = finished.getValue(members[0])
            val bobs = finished.getValue(members[1])
            assertNotEquals(
                alices.secretShare,
                bobs.secretShare,
                "the two profiles on this device are two members of the quorum, not one",
            )
            assertNotEquals(alices.hostPublicKey, bobs.hostPublicKey)
            assertEquals(
                alices.thresholdPublicKey,
                bobs.thresholdPublicKey,
                "of the same group's key",
            )
        }

    /**
     * The key both profiles signed up to is one the group can actually find: the room
     * it derives resolves to the same id for each of them, and each resolves it through
     * its own share rather than the other's.
     */
    @Test
    fun `each profile resolves the group's key as its own`(): Unit = runBlocking {
        openGroup()

        ceremonyId = ChillDkgRitualManager.proposeRitual(
            database = db,
            localChatRoom = members[0].room,
            userPublicKey = alice,
            nostrPrivateKey = aliceKeys.privKey!!,
            threshold = quorum,
        ).id

        runToCompletion()

        val thresholdKey = assertNotNull(
            sessionOf(members[0])?.thresholdPublicKey,
            "the ceremony has to have produced a key for this to be about anything",
        )
        val adminRoomId = SharedKeyDerivation.marmotGroupId(thresholdKey)

        listOf(alice, bob).forEach { profile ->
            val resolved = assertNotNull(
                FrostSigningManager.completedKey(db, adminRoomId, profile),
                "$profile holds a share and must resolve the group's key",
            )
            assertEquals(ceremonyId, resolved.id)
            assertEquals(
                profile,
                resolved.userPublicKey,
                "and must resolve it through its own row, not the other profile's",
            )
            assertTrue(FrostSigningManager.canSign(db, adminRoomId, profile))
        }

        // Carol is a member of the group but not a profile on this device, so this
        // device holds nothing of hers to resolve.
        assertEquals(
            null,
            FrostSigningManager.completedKey(db, adminRoomId, carol),
            "a member who is not a profile here has no row here",
        )
    }
}
