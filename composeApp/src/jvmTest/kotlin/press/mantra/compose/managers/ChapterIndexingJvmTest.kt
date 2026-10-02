package press.mantra.compose.managers

import androidx.room3.Room
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.PrivateKey
import fr.acinq.bitcoin.crypto.frost.Frost
import fr.acinq.bitcoin.crypto.frost.KeyMaterial
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
import press.mantra.compose.database.model.MantraChapter
import press.mantra.compose.database.model.MantraDialect
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
import press.mantra.compose.repository.FrostSigningRepository
import press.mantra.compose.repository.MantraRepository
import press.mantra.compose.text.Markdown

/**
 * A chapter's index has to be right when it is proposed, because it is signed
 * into the event and cannot be recounted on arrival.
 *
 * The test that matters here is the third one, and it fails against the old
 * `getChaptersForArtifactVersion(...).size`: three chapters proposed back to
 * back all read the same empty table and all get index 0. At one chapter at a
 * time that is a race nobody hits. An upload of twelve files hits it every
 * time, which is why this is a fix rather than a feature.
 */
class ChapterIndexingJvmTest {
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
    private val otherVersionId = "c".repeat(64)

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
            subject = "#Ekklesia",
            description = SharedKeyDerivation.describe("Admins of the group.")
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
                    userPublicKey = proposer,
                    participantPublicKey = member,
                    kind = DkgRitualEvents.HOST_KEY,
                    payload = hostKeys[index],
                )
            )
        }

        // The artifact a chapter hangs off, and a second version of it, so that
        // "chapters of this version" can be told from "chapters in this room".
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
        listOf(artifactVersionId to "1.0", otherVersionId to "2.0").forEach { (id, label) ->
            db.mantraArtifactVersionDao().upsert(
                MantraArtifactVersion(
                    id = id,
                    artifactId = artifactId,
                    publicKey = roomId,
                    versionLabel = label,
                    chatRoomId = roomId,
                    signature = "",
                )
            )
        }

        room = LocalChatRoom(chatRoom = chatRoom)
    }

    private suspend fun insertChapter(index: Int, versionId: String = artifactVersionId) {
        db.mantraChapterDao().upsert(
            MantraChapter(
                id = "${versionId.take(8)}chapter$index".padEnd(64, '0'),
                artifactVersionId = versionId,
                publicKey = roomId,
                name = "Chapter $index",
                originalText = "Signed already.",
                index = index,
                wordCount = 2,
                characterCount = 15,
                signature = "",
                chatRoomId = roomId,
            )
        )
    }

    /** Exactly what `AddChapterViewModel.addChapter` proposes, at a given index. */
    private suspend fun proposeChapterAt(index: Int, versionId: String = artifactVersionId) {
        val originalText = "Paragraph one.\n\nParagraph two."
        FrostSigningManager.proposeSigningBatch(
            database = db,
            localChatRoom = room,
            userPublicKey = proposer,
            lead = ChapterEvent.build(
                artifactVersionId = versionId,
                name = "Chapter $index",
                originalText = originalText,
                index = index,
                wordCount = Markdown.wordCount(originalText),
                characterCount = Markdown.characterCount(originalText),
            ),
            dependents = ChunkEvent::splitOf,
        )
    }

    private suspend fun nextIndex(versionId: String = artifactVersionId): Int =
        ChapterIndexing.nextIndex(
            mantraRepository = mantraRepository,
            frostSigningRepository = frostSigningRepository,
            chatRoomId = roomId,
            artifactVersionId = versionId,
        )

    @Test
    fun `an empty version starts at zero`() = runBlocking {
        openDevice()

        assertEquals(0, nextIndex())
    }

    @Test
    fun `signed chapters are counted`() = runBlocking {
        openDevice()
        insertChapter(0)
        insertChapter(1)

        assertEquals(2, nextIndex())
    }

    @Test
    fun `three chapters proposed back to back take three different indices`() = runBlocking {
        openDevice()

        // The whole point. Nothing is signed, so the table is empty throughout
        // and the old `.size` answered 0 three times.
        val allocated = (1..3).map {
            val index = nextIndex()
            proposeChapterAt(index)
            index
        }

        assertEquals(listOf(0, 1, 2), allocated)
    }

    @Test
    fun `a proposal counts alongside the rows`() = runBlocking {
        openDevice()
        insertChapter(0)
        proposeChapterAt(1)

        assertEquals(2, nextIndex())
    }

    @Test
    fun `one past the highest, not a count, so a gap is stepped over`() = runBlocking {
        openDevice()
        insertChapter(0)
        insertChapter(5)

        // A chapter that failed to reach a quorum spent its index: a re-proposed
        // chapter is a different event. Counting rows would hand out 2 and
        // collide with nothing, but it would also renumber a version whose
        // chapters are 0 and 5 into one whose next chapter is 2.
        assertEquals(6, nextIndex())
    }

    @Test
    fun `another version's chapters are not counted`() = runBlocking {
        openDevice()
        insertChapter(0, otherVersionId)
        insertChapter(1, otherVersionId)
        proposeChapterAt(7, otherVersionId)

        // Rows are already scoped by the query; proposals are not -- every
        // session in the room comes back together -- so the version tag is what
        // separates them, and getting it wrong would push every version's
        // numbering up by the whole room's.
        assertEquals(0, nextIndex())
        assertEquals(8, nextIndex(otherVersionId))
    }

    @Test
    fun `the chunks beside a chapter are not mistaken for chapters`() = runBlocking {
        openDevice()

        // A chapter proposal is a batch: one chapter and a chunk per paragraph.
        // A chunk carries an index too -- its position in the chapter -- and
        // reading those as chapter indices would have the second chapter of a
        // two-paragraph first chapter land at 2 instead of 1.
        proposeChapterAt(0)

        assertEquals(1, nextIndex())
    }
}
