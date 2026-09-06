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
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import press.mantra.compose.database.MantraDatabase
import press.mantra.compose.database.builder.getRoomDatabase
import press.mantra.compose.database.model.ChatRoom
import press.mantra.compose.database.model.DkgParticipantMessage
import press.mantra.compose.database.model.DkgSession
import press.mantra.compose.database.model.MantraChapter
import press.mantra.compose.database.model.NostrEvent
import press.mantra.compose.database.model.Profile
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.database.model.types.DkgRitualStage
import press.mantra.compose.extensions.toHex
import press.mantra.compose.nostr.dkg.DkgRitualEvents
import press.mantra.compose.nostr.nip30303.TranslationArtifactVersionEvent
import press.mantra.compose.nostr.nip30303.TranslationChapterEvent
import press.mantra.compose.ui.view.model.AddTranslationArtifactVersionViewModel

/**
 * What a translation proposal actually puts in front of the group.
 *
 * The same sharp edge `ChapterBatchProposalJvmTest` covers for a chapter and
 * its chunks, one level up: a translation chapter carries the id of the
 * translation it belongs to, and that id does not exist until the proposal
 * authors the translation under the group's key at the room's derivation path.
 * Both are resolved inside `proposeSigningBatch` and neither is knowable to the
 * caller, which is why the chapters are built from what it hands back.
 *
 * If that came apart, the translation would still be signed and every chapter
 * would still verify -- against a translation id nobody has. They would fail a
 * foreign key on the way in and the translation would simply arrive with no
 * chapters, which is a translation nobody can start.
 */
class TranslationBatchProposalJvmTest {
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

    /** The admin room: the group's key walked to the admin path, which is its id. */
    private val roomId = SharedKeyDerivation.marmotGroupId(thresholdPublicKey)

    private val ceremonyId = "ceremony".padEnd(64, '0')
    private val members = listOf("a", "b", "c").map { it.repeat(64) }
    private val hostKeys = listOf("2a", "2b", "2c").map { it.padEnd(66, '0') }

    private val proposer = members.first()
    private val artifactVersionId = "b".repeat(64)
    private val dialectId = "d".repeat(64)

    private lateinit var db: MantraDatabase
    private lateinit var room: LocalChatRoom

    @AfterTest
    fun closeDatabase() {
        if (::db.isInitialized) db.close()
    }

    /** The proposer's device: a room it can sign in, and the share to sign with. */
    private suspend fun openDevice() {
        db = getRoomDatabase(Room.inMemoryDatabaseBuilder<MantraDatabase>())

        // Profile hangs off a nostr event, and a room off a profile. Neither is
        // anything this test is about; they are the foreign keys in the way.
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
            subject = "#admins",
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

    /**
     * The source chapters, as the artifact already holds them. Only the id and
     * the position travel into the translation, so nothing else is furnished.
     */
    private fun sourceChapters(count: Int): List<MantraChapter> = (0 until count).map { index ->
        MantraChapter(
            id = "c$index".padEnd(64, 'f'),
            artifactVersionId = artifactVersionId,
            publicKey = roomId,
            name = "Chapter ${index + 1}",
            originalText = "Paragraph ${index + 1}.",
            index = index,
            wordCount = 2,
            characterCount = 13,
            signature = "0".repeat(128),
            chatRoomId = roomId
        )
    }

    private val translationTemplate = TranslationArtifactVersionEvent.build(
        artifactVersionId = artifactVersionId,
        dialectId = dialectId,
        name = "Sesotho",
        visibility = "private",
        license = "cc",
    )

    /** Exactly what `AddTranslationArtifactVersionViewModel.addTranslation` proposes. */
    private suspend fun proposeTranslation(chapters: List<MantraChapter>) =
        FrostSigningManager.proposeSigningBatch(
            database = db,
            localChatRoom = room,
            userPublicKey = proposer,
            lead = translationTemplate,
            dependents = { translation ->
                TranslationScaffold.chaptersOf(
                    translationArtifactVersionIds = listOf(translation.id),
                    chapters = chapters.map(TranslationScaffold.SourceChapter::of),
                    createdAt = translation.createdAt,
                )
            }
        )

    private suspend fun itemEvents(sessionId: String): List<Event> =
        db.frostSigningSessionDao().getItems(sessionId).map { Event.fromJson(it.unsignedEventJson) }

    @Test
    fun `the translation leads the batch and its chapters follow`() = runBlocking {
        openDevice()

        val events = itemEvents(proposeTranslation(sourceChapters(3)).id)

        // Item order is apply order, and a translation chapter row whose
        // translation does not exist yet is a foreign key violation. The
        // translation has to be first.
        assertEquals(4, events.size)
        assertEquals(TranslationArtifactVersionEvent.KIND, events.first().kind)
        assertTrue(events.drop(1).all { it.kind == TranslationChapterEvent.KIND })
    }

    @Test
    fun `every chapter names the translation as the group will author it`() = runBlocking {
        openDevice()

        val events = itemEvents(proposeTranslation(sourceChapters(3)).id)
        val translation = events.first()

        // The claim the whole `lead`/`dependents` form exists for. The id here is
        // a hash over the group's key at the room's path; a caller that built the
        // chapters before proposing could not have known it.
        val chapters = events.drop(1).map {
            TranslationChapterEvent(it.id, it.pubKey, it.createdAt, it.tags, it.content, it.sig)
        }

        assertTrue(chapters.all { it.translationArtifactVersionId() == translation.id })
    }

    @Test
    fun `each chapter keeps the source chapter it stands in for, and its place`() = runBlocking {
        openDevice()

        val source = sourceChapters(3)
        val events = itemEvents(proposeTranslation(source).id)
        val chapters = events.drop(1).map {
            TranslationChapterEvent(it.id, it.pubKey, it.createdAt, it.tags, it.content, it.sig)
        }

        // A translation chapter is only ever a place to hang translated chunks
        // off the right source chapter, in the right order. Lose either and the
        // translation reads as some other book.
        assertEquals(source.map { it.id }, chapters.map { it.chapterId() })
        assertEquals(source.map { it.index }, chapters.map { it.index() })
    }

    @Test
    fun `the whole batch is stamped and authored as one act by the room`() = runBlocking {
        openDevice()

        val events = itemEvents(proposeTranslation(sourceChapters(3)).id)

        // Signing runs at the path the room was derived at, so the author of
        // every item is the room's own id -- chapters included, which is what
        // lets one be checked without being told which key to expect.
        assertTrue(events.all { it.pubKey == roomId })
        assertTrue(events.none { it.pubKey == proposer })

        // And they carry the translation's timestamp rather than each reading
        // the clock, so the batch is one act rather than events that happen to
        // share a session.
        assertEquals(listOf(events.first().createdAt), events.map { it.createdAt }.distinct())
    }

    @Test
    fun `an artifact of the most chapters allowed still proposes`() = runBlocking {
        openDevice()

        val events = itemEvents(
            proposeTranslation(sourceChapters(FrostSigningManager.MAX_BATCH_SIZE - 1)).id
        )

        // The arithmetic the form is built on: the translation takes one of the
        // batch's places and the chapters get the rest. If this stopped being
        // true, the screen would offer a translation the session then refuses.
        assertEquals(
            FrostSigningManager.MAX_BATCH_SIZE,
            AddTranslationArtifactVersionViewModel.MAX_CHAPTERS_PER_TRANSLATION + 1
        )
        assertEquals(FrostSigningManager.MAX_BATCH_SIZE, events.size)
    }

    @Test
    fun `an artifact of one chapter too many is refused`() = runBlocking {
        openDevice()

        // Refused here rather than truncated: a batch that quietly dropped its
        // last chapters would sign a translation the group believes covers the
        // whole work while the end of it can never be translated. The screen
        // checks first so this is never what a member sees, but the screen is
        // not what enforces it.
        assertFailsWith<IllegalArgumentException> {
            proposeTranslation(sourceChapters(FrostSigningManager.MAX_BATCH_SIZE))
        }

        assertEquals(emptyList(), db.frostSigningSessionDao().getSessionsForChatRoom(roomId))
    }
}
