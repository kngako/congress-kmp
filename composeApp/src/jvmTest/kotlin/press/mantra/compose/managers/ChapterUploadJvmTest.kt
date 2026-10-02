package press.mantra.compose.managers

import androidx.room3.Room
import com.vitorpamplona.quartz.nip01Core.core.Event
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.PrivateKey
import fr.acinq.bitcoin.crypto.frost.Frost
import fr.acinq.bitcoin.crypto.frost.KeyMaterial
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import press.mantra.compose.database.MantraDatabase
import press.mantra.compose.database.builder.getRoomDatabase
import press.mantra.compose.database.model.ChatRoom
import press.mantra.compose.database.model.DkgParticipantMessage
import press.mantra.compose.database.model.DkgSession
import press.mantra.compose.database.model.MantraArtifact
import press.mantra.compose.database.model.MantraArtifactVersion
import press.mantra.compose.database.model.MantraDialect
import press.mantra.compose.database.model.MantraTranslationArtifactVersion
import press.mantra.compose.database.model.NostrEvent
import press.mantra.compose.database.model.Profile
import press.mantra.compose.database.model.types.DkgRitualStage
import press.mantra.compose.database.repository.DatabaseChatRepository
import press.mantra.compose.database.repository.DatabaseFrostSigningRepository
import press.mantra.compose.database.repository.DatabaseMantraRepository
import press.mantra.compose.extensions.toHex
import press.mantra.compose.files.PickedFile
import press.mantra.compose.nostr.dkg.DkgRitualEvents
import press.mantra.compose.nostr.nip30303.ChapterEvent
import press.mantra.compose.nostr.nip30303.ChunkEvent
import press.mantra.compose.nostr.nip30303.TranslationChapterEvent
import press.mantra.compose.text.Markdown
import press.mantra.compose.ui.view.model.UploadChaptersViewModel
import press.mantra.compose.ui.view.state.UploadChaptersUIState
import press.mantra.compose.ui.view.state.UploadProblem

/**
 * An upload, end to end, over a real database.
 *
 * `UploadReviewTest` covers what a file is offered as, which is pure. This is
 * the orchestration: how many sessions a group is asked for, what each one
 * signs, what index each chapter takes, and -- the part with no second chance --
 * what is left behind when a pass only half works.
 *
 * Nothing can retract a proposal, so partial success is the expected outcome
 * rather than an error, and the only thing that makes it recoverable is the
 * screen keeping an honest list of what went and what did not.
 */
class ChapterUploadJvmTest {
    private val participants = 3
    private val threshold = 2

    private val keyMaterial: KeyMaterial = Frost.trustedDealerKeygen(
        thresholdSecretKey = PrivateKey(
            ByteVector32("1c0ffee0000000000000000000000000000000000000000000000000000000a1")
        ),
        nParticipants = participants,
        threshold = threshold,
    )

    private val thresholdPublicKey = keyMaterial.thresholdPublicKey.value.toHex()
    private val roomId = SharedKeyDerivation.marmotGroupId(thresholdPublicKey)

    private val ceremonyId = "ceremony".padEnd(64, '0')
    private val members = listOf("a", "b", "c").map { it.repeat(64) }
    private val hostKeys = listOf("2a", "2b", "2c").map { it.padEnd(66, '0') }

    private val proposer = members.first()
    private val dialectId = "d".repeat(64)
    private val artifactId = "a1".padEnd(64, '0')
    private val artifactVersionId = "b".repeat(64)

    private lateinit var db: MantraDatabase
    private lateinit var viewModel: UploadChaptersViewModel

    @BeforeTest
    fun setUpMain() {
        // viewModelScope dispatches on Dispatchers.Main, which a jvm test has
        // none of. Unconfined rather than a test scheduler: the view model does
        // its work on Dispatchers.IO and uses Main only to hand a result back,
        // so there is nothing here to advance -- the test waits on the callback.
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        if (::db.isInitialized) db.close()
    }

    /** Opens a device, with the group's shared key present only if [withKey]. */
    private suspend fun openDevice(withKey: Boolean = true, translations: Int = 0) {
        db = getRoomDatabase(Room.inMemoryDatabaseBuilder<MantraDatabase>())
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

        val nostrEventId = "e$proposer".take(64)
        db.nostrEventDao().upsert(
            NostrEvent(
                id = nostrEventId,
                pubKey = proposer,
                kind = 0,
                tags = emptyArray(),
                content = "{}",
                sig = "0".repeat(128),
            )
        )
        db.profileDao().upsert(Profile(publicKey = proposer, nostrEventId = nostrEventId))

        db.chatRoomDao().upsert(
            ChatRoom(
                id = roomId,
                subject = "#Ekklesia",
                description = SharedKeyDerivation.describe("Admins of the group.")
            )
        )

        if (withKey) giveTheGroupItsKey()

        db.mantraDialectDao().upsert(
            MantraDialect(
                id = dialectId,
                publicKey = roomId,
                name = "Setswana",
                country = "BW",
                language = "tn",
                signature = "",
                chatRoomId = roomId,
            )
        )
        db.mantraArtifactDao().upsert(
            MantraArtifact(
                id = artifactId,
                publicKey = roomId,
                name = "To Kill a Mockingbird",
                url = "https://example.test/mockingbird",
                visibility = "private",
                dialectId = dialectId,
                license = "cc",
                chatRoomId = roomId,
                signature = "",
            )
        )
        db.mantraArtifactVersionDao().upsert(
            MantraArtifactVersion(
                id = artifactVersionId,
                artifactId = artifactId,
                publicKey = roomId,
                versionLabel = "1.0",
                chatRoomId = roomId,
                signature = "",
            )
        )
        (0 until translations).forEach { ordinal ->
            db.mantraTranslationArtifactVersionDao().upsert(
                MantraTranslationArtifactVersion(
                    id = "translation$ordinal".padEnd(64, '0'),
                    publicKey = roomId,
                    artifactVersionId = artifactVersionId,
                    dialectId = dialectId,
                    name = "Translation $ordinal",
                    visibility = "private",
                    license = "cc",
                    signature = "",
                    chatRoomId = roomId,
                )
            )
        }

        viewModel = UploadChaptersViewModel(
            artifactId = artifactId,
            chatRoomId = roomId,
            activeUserPublicKey = proposer,
            relayHint = null,
            initialUIState = UploadChaptersUIState.Loading,
            mantraRepository = DatabaseMantraRepository(db, scope),
            chatRepository = DatabaseChatRepository(db, scope),
            frostSigningRepository = DatabaseFrostSigningRepository(db, scope),
        )
        viewModel.initiate()
        awaitLoaded()
    }

    private suspend fun giveTheGroupItsKey() {
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
                publicShares = keyMaterial.publicShares.joinToString(",") { it.value.toHex() },
            )
        )
        members.forEachIndexed { index, member ->
            db.dkgSessionDao().upsert(
                DkgParticipantMessage(
                    sessionId = ceremonyId,
                    userPublicKey = proposer,
                    participantPublicKey = member,
                    kind = DkgRitualEvents.HOST_KEY,
                    payload = hostKeys[index],
                )
            )
        }
    }

    private suspend fun awaitLoaded(): UploadChaptersUIState.Loaded =
        withTimeout(10_000) {
            var state = viewModel.uiState.value
            while (state !is UploadChaptersUIState.Loaded) {
                kotlinx.coroutines.delay(10)
                state = viewModel.uiState.value
            }
            state
        }

    private val loaded: UploadChaptersUIState.Loaded
        get() = viewModel.uiState.value as UploadChaptersUIState.Loaded

    private fun file(name: String, text: String) =
        PickedFile(name = name, bytes = text.encodeToByteArray())

    /** A chapter of [paragraphs] paragraphs, as a file would hold it. */
    private fun chapterFile(ordinal: Int, paragraphs: Int = 2) = file(
        name = "chapter-$ordinal.md",
        text = (1..paragraphs).joinToString("\n\n") { "Paragraph $it of chapter $ordinal." },
    )

    /** Proposes, and waits for the pass to finish. */
    private suspend fun propose(): Pair<Int, Int> {
        val done = CompletableDeferred<Pair<Int, Int>>()
        viewModel.propose(
            localChatRoom = loaded.localChatRoom,
            artifactVersion = loaded.artifactVersion!!,
        ) { proposed, failed -> done.complete(proposed to failed) }
        return withTimeout(30_000) { done.await() }
    }

    private suspend fun itemEvents(sessionId: String): List<Event> =
        db.frostSigningSessionDao().getItems(sessionId).map { Event.fromJson(it.unsignedEventJson) }

    private suspend fun sessionsInRoom(): List<String> =
        db.frostSigningSessionDao().getSessionsForChatRoom(roomId).map { it.id }

    @Test
    fun `five files become five sessions, one chapter each, numbered in order`() = runBlocking {
        openDevice()

        viewModel.addFiles((1..5).map { chapterFile(it, paragraphs = 3) })
        assertEquals(5, loaded.chapters.size)

        val (proposed, failed) = propose()

        assertEquals(5 to 0, proposed to failed)

        val chapterSessions = loaded.chapters.map { it.sessionId!! }
        assertEquals(5, chapterSessions.toSet().size)

        // Each session is one chapter leading its own chunks -- never two
        // chapters sharing a batch, which is the whole shape of this feature.
        val chapters = chapterSessions.map { session ->
            val events = itemEvents(session)
            assertEquals(ChapterEvent.KIND, events.first().kind)
            assertTrue(events.drop(1).all { it.kind == ChunkEvent.KIND })
            assertEquals(4, events.size) // the chapter and its three chunks
            ChapterEvent(
                events.first().id,
                events.first().pubKey,
                events.first().createdAt,
                events.first().tags,
                events.first().content,
                events.first().sig,
            )
        }

        // Contiguous from zero, which the old `.size` numbering could not do:
        // nothing is signed, so the table is empty for all five.
        assertEquals(listOf(0, 1, 2, 3, 4), chapters.map { it.index() })
        assertEquals(
            (1..5).map { "chapter-$it" },
            chapters.map { it.name() },
        )
    }

    @Test
    fun `each chapter's chunks are the blocks of its own file`() = runBlocking {
        openDevice()

        val text = "# A heading\n\nFirst paragraph.\n\n- one\n- two\n"
        viewModel.addFiles(listOf(file("ch.md", text)))
        propose()

        val events = itemEvents(loaded.chapters.single().sessionId!!)
        val chunks = events.drop(1).map { it.content }

        assertEquals(Markdown.splitChunks(text), chunks)
        // And the block markers are off, which is what Phase 8 changed.
        assertEquals(listOf("A heading", "First paragraph.", "one", "two"), chunks)
    }

    @Test
    fun `the whole upload asks for one scaffolding session, not one per chapter`() = runBlocking {
        openDevice(translations = 3)

        viewModel.addFiles((1..4).map { chapterFile(it) })
        propose()

        val chapterSessions = loaded.chapters.map { it.sessionId!! }.toSet()
        val scaffolding = sessionsInRoom().filterNot { it in chapterSessions }

        assertEquals(1, scaffolding.size)

        val rows = itemEvents(scaffolding.single())
        assertEquals(12, rows.size) // four chapters x three dialects
        assertTrue(rows.all { it.kind == TranslationChapterEvent.KIND })
    }

    @Test
    fun `a pass that fails leaves every row listed with its reason and retryable`() = runBlocking {
        // No shared key, so every proposal throws inside the view model. This is
        // the reachable way to half-fail a pass: the caps are checked in the
        // form, so no input gets past the review and then fails the manager.
        openDevice(withKey = false)

        viewModel.addFiles((1..3).map { chapterFile(it) })

        val (proposed, failed) = propose()
        assertEquals(0 to 3, proposed to failed)

        // Nothing proposed, nothing lost, and each row says why.
        assertEquals(emptyList(), sessionsInRoom())
        assertTrue(loaded.chapters.all { it.sessionId == null })
        assertTrue(loaded.chapters.all { it.problem == UploadProblem.ProposalFailed })

        // ProposalFailed does not block, because a retry is a new proposal --
        // which is the only correct way to retry a FROST session.
        assertEquals(3, loaded.proposable.size)
    }

    @Test
    fun `retrying after a failure proposes the rest and numbers them from zero`() = runBlocking {
        openDevice(withKey = false)

        viewModel.addFiles((1..3).map { chapterFile(it) })
        propose()

        giveTheGroupItsKey()

        val (proposed, failed) = propose()

        assertEquals(3 to 0, proposed to failed)
        assertTrue(loaded.chapters.all { it.problem == null })

        val indices = loaded.chapters.map { row ->
            val lead = itemEvents(row.sessionId!!).first()
            ChapterEvent(
                lead.id, lead.pubKey, lead.createdAt, lead.tags, lead.content, lead.sig
            ).index()
        }

        // The failed pass spent no indices, because it created nothing.
        assertEquals(listOf(0, 1, 2), indices)
    }

    @Test
    fun `a row already proposed is not proposed again`() = runBlocking {
        openDevice()

        viewModel.addFiles(listOf(chapterFile(1)))
        propose()
        val first = loaded.chapters.single().sessionId

        // Nothing left to do, so a second tap proposes nothing rather than
        // asking the group to sign the same chapter twice.
        val (proposed, failed) = propose()

        assertEquals(0 to 0, proposed to failed)
        assertEquals(first, loaded.chapters.single().sessionId)
        assertEquals(1, sessionsInRoom().size)
    }

    @Test
    fun `a file that is not text is never proposed and does not stop the others`() = runBlocking {
        openDevice()

        viewModel.addFiles(
            listOf(
                chapterFile(1),
                PickedFile("cover.png", byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x00)),
                chapterFile(2),
            )
        )

        val (proposed, failed) = propose()

        // Two proposed, and the third is not a failure -- it was never offered.
        assertEquals(2 to 0, proposed to failed)
        assertEquals(UploadProblem.NotText, loaded.chapters[1].problem)
        assertNull(loaded.chapters[1].sessionId)
        assertEquals(2, loaded.chapters.count { it.sessionId != null })
    }

    @Test
    fun `the soft cap is on the upload, not on the artifact`() = runBlocking {
        openDevice()

        viewModel.addFiles((1..UploadChaptersViewModel.MAX_FILES_PER_UPLOAD + 5).map { chapterFile(it) })

        // The extra files are dropped at the door rather than listed and
        // refused: a limit on how much of the room's attention one tap spends.
        assertEquals(UploadChaptersViewModel.MAX_FILES_PER_UPLOAD, loaded.chapters.size)
    }
}
