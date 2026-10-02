package press.mantra.compose.managers

import androidx.room3.Room
import com.vitorpamplona.quartz.nip01Core.core.Event
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.PrivateKey
import fr.acinq.bitcoin.crypto.frost.Frost
import fr.acinq.bitcoin.crypto.frost.KeyMaterial
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import press.mantra.compose.database.MantraDatabase
import press.mantra.compose.database.builder.getRoomDatabase
import press.mantra.compose.database.model.ChatRoom
import press.mantra.compose.database.model.DkgParticipantMessage
import press.mantra.compose.database.model.DkgSession
import press.mantra.compose.database.model.MantraDialect
import press.mantra.compose.database.model.NostrEvent
import press.mantra.compose.database.model.Profile
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.database.model.types.DkgRitualStage
import press.mantra.compose.extensions.toHex
import press.mantra.compose.language.AfricanLanguage
import press.mantra.compose.language.AfricanLanguages
import press.mantra.compose.nostr.dkg.DkgRitualEvents
import press.mantra.compose.nostr.nip30303.DialectEvent

/**
 * What a wave of dialects actually puts in front of the group.
 *
 * `DialectScaffoldTest` builds the templates and asserts their shape, which is
 * the easy half. This runs the real proposal against a real database and a real
 * key, because the half with the sharp edge is the one the caller cannot see:
 * every event of a wave is re-authored under the group's key at the room's
 * derivation path, inside `proposeSigningBatch`, and the ids the group will sign
 * are not the ids `dialectsOf` produced.
 *
 * The wave is also the first batch in this app made of many *peers* -- fifty
 * dialects, no lead and no dependents -- rather than one thing and its parts.
 */
class DialectWaveProposalJvmTest {
    private val participants = 3
    private val threshold = 2

    /** Stands in for a completed ceremony; the test is about the proposal, not the DKG. */
    private val keyMaterial: KeyMaterial = Frost.trustedDealerKeygen(
        thresholdSecretKey = PrivateKey(
            ByteVector32("1c0ffee0000000000000000000000000000000000000000000000000000000a1")
        ),
        nParticipants = participants,
        threshold = threshold
    )

    private val thresholdPublicKey = keyMaterial.thresholdPublicKey.value.toHex()

    private val roomId = SharedKeyDerivation.marmotGroupId(thresholdPublicKey)

    private val ceremonyId = "ceremony".padEnd(64, '0')
    private val members = listOf("a", "b", "c").map { it.repeat(64) }
    private val hostKeys = listOf("2a", "2b", "2c").map { it.padEnd(66, '0') }

    private val proposer = members.first()

    private lateinit var db: MantraDatabase
    private lateinit var room: LocalChatRoom

    @AfterTest
    fun closeDatabase() {
        if (::db.isInitialized) db.close()
    }

    /** The proposer's device: a room it can sign in, and the share to sign with. */
    private suspend fun openDevice() {
        db = getRoomDatabase(Room.inMemoryDatabaseBuilder<MantraDatabase>())

        val nostrEventId = "e$proposer".take(64)
        db.nostrEventDao().upsert(
            NostrEvent(
                id = nostrEventId,
                pubKey = proposer,
                kind = 0,
                tags = emptyArray(),
                content = "{}",
                sig = "0".repeat(128)
            )
        )
        db.profileDao().upsert(Profile(publicKey = proposer, nostrEventId = nostrEventId))

        val chatRoom = ChatRoom(
            id = roomId,
            userPublicKey = proposer,
            subject = "#Ekklesia",
            description = SharedKeyDerivation.describe("Admins of the group."),
            mlsGroupState = null
        )
        db.chatRoomDao().upsert(chatRoom)

        db.dkgSessionDao().upsert(
            DkgSession(
                id = ceremonyId,
                chatRoomId = roomId,
                coordinatorPublicKey = proposer,
                userPublicKey = proposer,
                threshold = threshold,
                participantCount = participants,
                stage = DkgRitualStage.COMPLETE,
                hostPublicKey = hostKeys[0],
                round1Random = "1".repeat(64),
                round2AuxRandom = "2".repeat(64),
                thresholdPublicKey = thresholdPublicKey,
                secretShare = keyMaterial.secretShares[0].value.toHex(),
                publicShares = keyMaterial.publicShares.joinToString(",") { it.value.toHex() }
            )
        )

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

        room = LocalChatRoom(chatRoom = chatRoom)
    }

    /** Exactly what `AddDialectsFromCatalogueViewModel` proposes, for one wave. */
    private suspend fun proposeWave(languages: List<AfricanLanguage>, createdAt: Long = STAMP) =
        FrostSigningManager.proposeSigningBatch(
            database = db,
            localChatRoom = room,
            userPublicKey = proposer,
            events = DialectScaffold.dialectsOf(languages = languages, createdAt = createdAt),
        )

    private suspend fun itemEvents(sessionId: String): List<Event> =
        db.frostSigningSessionDao().getItems(sessionId).map { Event.fromJson(it.unsignedEventJson) }

    private fun dialectsOf(events: List<Event>) = events.map {
        DialectEvent(it.id, it.pubKey, it.createdAt, it.tags, it.content, it.sig)
    }

    @Test
    fun `a wave is one session with one item per entry, in order`() = runBlocking {
        openDevice()

        val wave = AfricanLanguages.ALL.take(5)
        val events = itemEvents(proposeWave(wave).id)

        assertEquals(wave.size, events.size)
        assertTrue(events.all { it.kind == DialectEvent.KIND })
        assertEquals(wave.map { it.name }, dialectsOf(events).map { it.name() })
    }

    @Test
    fun `every item carries the entry it came from`() = runBlocking {
        openDevice()

        val wave = AfricanLanguages.ALL.take(8)
        val dialects = dialectsOf(itemEvents(proposeWave(wave).id))

        assertEquals(
            wave,
            dialects.map {
                AfricanLanguage(
                    name = it.name().orEmpty(),
                    code = it.language().orEmpty(),
                    country = it.country().orEmpty(),
                )
            }
        )
    }

    @Test
    fun `the whole wave is authored by the room, not by whoever ticked it`() = runBlocking {
        openDevice()

        val events = itemEvents(proposeWave(AfricanLanguages.ALL.take(4)).id)

        // Signing runs at the path the room was derived at, so a dialect says
        // which group made it simply by being signed.
        assertTrue(events.all { it.pubKey == roomId })
        assertTrue(events.none { it.pubKey == proposer })
    }

    @Test
    fun `one wave is one timestamp`() = runBlocking {
        openDevice()

        val events = itemEvents(proposeWave(AfricanLanguages.ALL.take(6)).id)

        assertEquals(setOf(STAMP), events.map { it.createdAt }.toSet())
    }

    /**
     * The ids the group will sign are not the ids the templates had, because the
     * author is resolved inside the proposal. Distinct all the same: the entries
     * share an author and a timestamp by construction, so their tags are the only
     * thing separating them.
     */
    @Test
    fun `items of one wave get distinct ids`() = runBlocking {
        openDevice()

        val events = itemEvents(proposeWave(AfricanLanguages.ALL.take(20)).id)

        assertEquals(events.size, events.map { it.id }.distinct().size)
    }

    @Test
    fun `the whole catalogue proposes in a single session`() = runBlocking {
        openDevice()

        val events = itemEvents(proposeWave(AfricanLanguages.ALL).id)

        assertEquals(AfricanLanguages.ALL.size, events.size)
    }

    /**
     * A selection bigger than a batch is chunked rather than refused, which is
     * what the view model does with `chunked(MAX_BATCH_SIZE)`. Asserted here
     * because the catalogue is smaller than one batch today, so nothing else
     * exercises it.
     */
    @Test
    fun `a selection larger than a batch becomes several sessions`() = runBlocking {
        openDevice()

        val oversized = (0 until FrostSigningManager.MAX_BATCH_SIZE + 1).map { index ->
            AfricanLanguage(name = "Language $index", code = "x$index", country = "Nowhere")
        }

        val sessions = oversized.chunked(FrostSigningManager.MAX_BATCH_SIZE).map {
            proposeWave(it)
        }

        assertEquals(2, sessions.size)
        assertEquals(
            listOf(FrostSigningManager.MAX_BATCH_SIZE, 1),
            sessions.map { itemEvents(it.id).size }
        )
    }

    /**
     * The round trip the picker depends on: the rows a signed wave becomes say
     * what was ticked.
     *
     * Against the *proposed* events rather than the templates, since those are
     * the ones the group signs -- and `fromDialectEvent` is the arm
     * `ChatMessage.applyInnerEvent` will take when it does.
     */
    @Test
    fun `a proposed wave parses back into the dialects that were ticked`() = runBlocking {
        openDevice()

        val wave = AfricanLanguages.ALL.take(12)
        val events = itemEvents(proposeWave(wave).id)

        val rows = events.mapNotNull { event ->
            MantraDialect.fromDialectEvent(
                dialectEvent = DialectEvent(
                    event.id, event.pubKey, event.createdAt, event.tags, event.content, event.sig
                ),
                chatRoomId = roomId,
            )
        }

        assertEquals(wave.size, rows.size)
        assertEquals(
            wave,
            rows.map { AfricanLanguage(name = it.name, code = it.language, country = it.country) }
        )

        // And so the picker would now show every one of them as held rather than
        // offering them again.
        assertTrue(
            DialectScaffold.missingFrom(existing = rows, catalogue = wave).isEmpty()
        )
    }

    private companion object {
        const val STAMP = 1_700_000_000L
    }
}
