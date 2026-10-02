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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
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
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.database.model.types.DkgRitualStage
import press.mantra.compose.database.repository.DatabaseFrostSigningRepository
import press.mantra.compose.database.repository.DatabaseMantraRepository
import press.mantra.compose.extensions.toHex
import press.mantra.compose.nostr.dkg.DkgRitualEvents
import press.mantra.compose.nostr.nip30303.ChapterEvent
import press.mantra.compose.nostr.nip30303.ChunkEvent
import press.mantra.compose.nostr.nip30303.TranslationChapterEvent
import press.mantra.compose.repository.FrostSigningRepository
import press.mantra.compose.repository.MantraRepository
import press.mantra.compose.text.Markdown

/**
 * How many sessions a group is asked for when chapters arrive together.
 *
 * The scaffolding has to be its own session -- a chapter's paragraph budget
 * should not be spent on the group's dialects -- but it does not have to be one
 * session *per chapter*, and that only becomes visible above one chapter. The
 * first test here is the arithmetic the upload rests on: twelve chapters and
 * three dialects is thirty-six join rows, which is one batch rather than
 * twelve.
 */
class ChapterScaffoldingJvmTest {
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
    private lateinit var room: LocalChatRoom
    private lateinit var mantraRepository: MantraRepository
    private lateinit var frostSigningRepository: FrostSigningRepository

    @AfterTest
    fun closeDatabase() {
        if (::db.isInitialized) db.close()
    }

    private suspend fun openDevice() {
        db = getRoomDatabase(Room.inMemoryDatabaseBuilder<MantraDatabase>())
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        mantraRepository = DatabaseMantraRepository(db, scope)
        frostSigningRepository = DatabaseFrostSigningRepository(db, scope)

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

        val chatRoom = ChatRoom(
            id = roomId,
            userPublicKey = proposer,
            subject = "#Ekklesia",
            description = SharedKeyDerivation.describe("Admins of the group."),
            mlsGroupState = null,
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
                publicShares = keyMaterial.publicShares.joinToString(",") { it.value.toHex() },
            )
        )
        members.forEachIndexed { index, member ->
            db.dkgSessionDao().upsert(
                DkgParticipantMessage(
                    sessionId = ceremonyId,
                    participantPublicKey = member,
                    kind = DkgRitualEvents.HOST_KEY,
                    payload = hostKeys[index],
                )
            )
        }

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

        room = LocalChatRoom(chatRoom = chatRoom)
    }

    private suspend fun addTranslations(count: Int) {
        (0 until count).forEach { ordinal ->
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
    }

    /** Proposes [count] chapters the way the upload screen does, in index order. */
    private suspend fun proposeChapters(count: Int): List<ChapterScaffolding.ProposedChapter> =
        (0 until count).map { index ->
            val originalText = "Paragraph one.\n\nParagraph two."
            val session = FrostSigningManager.proposeSigningBatch(
                database = db,
                localChatRoom = room,
                userPublicKey = proposer,
                lead = ChapterEvent.build(
                    artifactVersionId = artifactVersionId,
                    name = "Chapter $index",
                    originalText = originalText,
                    index = index,
                    wordCount = Markdown.wordCount(originalText),
                    characterCount = Markdown.characterCount(originalText),
                ),
                dependents = ChunkEvent::splitOf,
            )
            ChapterScaffolding.ProposedChapter(sessionId = session.id, index = index)
        }

    private suspend fun scaffold(chapters: List<ChapterScaffolding.ProposedChapter>) =
        ChapterScaffolding.propose(
            mantraRepository = mantraRepository,
            frostSigningRepository = frostSigningRepository,
            localChatRoom = room,
            userPublicKey = proposer,
            artifactVersionId = artifactVersionId,
            chapters = chapters,
        )

    private suspend fun itemEvents(sessionId: String): List<Event> =
        db.frostSigningSessionDao().getItems(sessionId).map { Event.fromJson(it.unsignedEventJson) }

    @Test
    fun `twelve chapters and three dialects is one scaffolding session`() = runBlocking {
        openDevice()
        addTranslations(3)

        val sessions = scaffold(proposeChapters(12))

        // The arithmetic the upload rests on. Thirty-six join rows fit in one
        // batch; a session per chapter would have been twelve of them carrying
        // three rows each.
        assertEquals(1, sessions.size)
        assertEquals(36, itemEvents(sessions.single()).size)
    }

    @Test
    fun `every row is a translation chapter naming a chapter the group will author`() =
        runBlocking {
            openDevice()
            addTranslations(2)

            val chapters = proposeChapters(2)
            val sessions = scaffold(chapters)
            val rows = itemEvents(sessions.single())

            assertTrue(rows.all { it.kind == TranslationChapterEvent.KIND })

            // The ids come from the chapters' own sessions, which is the only
            // place they exist before a quorum signs: a hash over the group's
            // key at the room's path.
            val chapterIds = chapters.map { proposed ->
                itemEvents(proposed.sessionId).first().id
            }
            val named = rows.map { row ->
                TranslationChapterEvent(
                    row.id, row.pubKey, row.createdAt, row.tags, row.content, row.sig
                ).chapterId()
            }

            assertEquals(chapterIds.toSet(), named.toSet())
        }

    @Test
    fun `a chapter keeps its own position, not the list's`() = runBlocking {
        openDevice()
        addTranslations(1)

        // A catch-up proposes a subset, so counting the list would renumber the
        // chapters it happens to be filling in.
        val sessions = scaffold(
            listOf(
                ChapterScaffolding.ProposedChapter(
                    sessionId = proposeChapters(1).single().sessionId,
                    index = 7,
                )
            )
        )

        val row = itemEvents(sessions.single()).single()
        assertEquals(
            7,
            TranslationChapterEvent(
                row.id, row.pubKey, row.createdAt, row.tags, row.content, row.sig
            ).index(),
        )
    }

    @Test
    fun `the cross product is what gets chunked, not the translations`() = runBlocking {
        openDevice()
        addTranslations(3)

        // 25 chapters x 3 dialects is 75 rows, which is two batches. Chunking
        // the translations instead would have put all 75 in one and thrown --
        // which is the bug this generalisation fixes, and it only appears above
        // one chapter.
        val sessions = scaffold(proposeChapters(25))

        assertEquals(2, sessions.size)
        assertEquals(
            FrostSigningManager.MAX_BATCH_SIZE,
            itemEvents(sessions.first()).size,
        )
        assertEquals(75 - FrostSigningManager.MAX_BATCH_SIZE, itemEvents(sessions.last()).size)
    }

    @Test
    fun `a version with no translations is asked for nothing`() = runBlocking {
        openDevice()

        assertEquals(emptyList(), scaffold(proposeChapters(3)))
    }

    @Test
    fun `no chapters is asked for nothing`() = runBlocking {
        openDevice()
        addTranslations(3)

        assertEquals(emptyList(), scaffold(emptyList()))
    }
}
