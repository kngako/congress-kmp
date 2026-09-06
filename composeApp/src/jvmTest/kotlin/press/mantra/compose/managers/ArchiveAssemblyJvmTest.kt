package press.mantra.compose.managers

import androidx.room3.Room
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.PrivateKey
import fr.acinq.bitcoin.crypto.frost.Frost
import fr.acinq.bitcoin.crypto.frost.IndividualNonce
import fr.acinq.bitcoin.crypto.frost.KeyMaterial
import fr.acinq.bitcoin.crypto.frost.SecretNonce
import fr.acinq.bitcoin.crypto.frost.Session
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import press.mantra.compose.database.MantraDatabase
import press.mantra.compose.database.builder.getRoomDatabase
import press.mantra.compose.database.model.ChatMessage
import press.mantra.compose.database.model.ChatRoom
import press.mantra.compose.database.model.MantraDialect
import press.mantra.compose.database.model.NostrEvent
import press.mantra.compose.database.model.Profile
import press.mantra.compose.extensions.toHex
import press.mantra.compose.nostr.archive.ArchiveEvent
import press.mantra.compose.nostr.frost.GroupKeyStateEvent
import press.mantra.compose.nostr.nip30303.ArtifactEvent
import press.mantra.compose.nostr.nip30303.ArtifactVersionEvent
import press.mantra.compose.nostr.nip30303.ChapterEvent
import press.mantra.compose.nostr.nip30303.ChunkEvent
import press.mantra.compose.nostr.nip30303.DialectEvent
import press.mantra.compose.nostr.nip30303.TranslationArtifactVersionEvent
import press.mantra.compose.nostr.nip30303.TranslationChapterEvent
import press.mantra.compose.nostr.nip30303.TranslationChunkEvent

/**
 * Assembling a room's archive out of the rows a device actually holds.
 *
 * The seed is the real inbound path -- `ChatMessage.applyInnerEvent`, the same
 * call a finished signing session makes -- so what is archived here is what a
 * member's database really contains rather than rows built to suit the test.
 * That matters because the whole risk in this direction is a rebuild that
 * differs from what was signed, and rows assembled by hand would hide it.
 */
class ArchiveAssemblyJvmTest {
    private val db: MantraDatabase = getRoomDatabase(Room.inMemoryDatabaseBuilder<MantraDatabase>())

    @AfterTest
    fun closeDb() = db.close()

    private val participants = 3
    private val threshold = 2

    private val keyMaterial: KeyMaterial = Frost.trustedDealerKeygen(
        thresholdSecretKey = PrivateKey(
            ByteVector32("1c0ffee0000000000000000000000000000000000000000000000000000000a1")
        ),
        nParticipants = participants,
        threshold = threshold
    )

    private val room: SharedKeyDerivation.Derived = SharedKeyDerivation.derive(
        thresholdPublicKey = keyMaterial.thresholdPublicKey.value.toHex(),
        path = SharedKeyDerivation.MARMOT_ADMIN_GROUP_PATH
    )

    /** The room's id and the key it signs as are one value. */
    private val chatRoomId = room.hex

    private val newMember = "9".repeat(64)

    private fun groupSignature(eventId: String): String {
        val message = ByteVector(eventId.hexToByteArray())
        val signerIds = listOf(0, 1)

        val nonces = signerIds.map { signerId ->
            SecretNonce.generate(
                sessionRandom = ByteVector32("a".repeat(63) + "${signerId + 1}"),
                secretShare = keyMaterial.secretShares[signerId],
                publicShare = keyMaterial.publicShares[signerId],
                tweakedThresholdPublicKey = room.cache.tweakedPublicKey,
                message = message,
                extraInput = null
            )
        }

        val signingSession = Session.create(
            aggregatedNonce = IndividualNonce.aggregate(nonces.map { it.second }).right!!,
            signerIds = signerIds.map { it.toUInt() },
            signerPublicShares = signerIds.map { keyMaterial.publicShares[it] },
            nParticipants = participants,
            threshold = threshold,
            tweakCache = room.cache,
            message = message
        )

        val partials = signerIds.mapIndexed { position, signerId ->
            signingSession.sign(
                nonces[position].first,
                keyMaterial.secretShares[signerId],
                signerId.toUInt()
            ).right!!
        }

        return signingSession.aggregateSigs(partials).right!!.toByteArray().toHex()
    }

    /** A template as a finished session leaves it: authored and signed by the room. */
    private fun signed(template: EventTemplate<*>): Event {
        val id = EventHasher.hashId(
            pubKey = chatRoomId,
            createdAt = template.createdAt,
            kind = template.kind,
            tags = template.tags,
            content = template.content
        )

        return Event(
            id = id,
            pubKey = chatRoomId,
            createdAt = template.createdAt,
            kind = template.kind,
            tags = template.tags,
            content = template.content,
            sig = groupSignature(id)
        )
    }

    /** What `FrostSigningManager.applySignedEvent` does, through the same call. */
    private suspend fun apply(template: EventTemplate<*>): Event {
        val event = signed(template)

        ChatMessage.applyInnerEvent(
            database = db,
            groupId = chatRoomId,
            event = event,
            marmotGroupEventId = null,
            marmotInnerEventId = null,
            senderPublicKey = chatRoomId,
            isUserMessage = false,
            createdAt = Instant.fromEpochSeconds(event.createdAt)
        )

        return event
    }

    /** ChatRoom -> Profile -> NostrEvent, the foreign key chain a room hangs off. */
    private suspend fun seedRoom() {
        val nostrEventId = "c".repeat(64)
        db.nostrEventDao().upsert(
            NostrEvent(
                id = nostrEventId,
                pubKey = newMember,
                kind = 0,
                tags = emptyArray(),
                content = "{}",
                sig = "0".repeat(128),
            )
        )
        db.profileDao().upsert(
            Profile(publicKey = newMember, userName = "member", nostrEventId = nostrEventId)
        )
        db.chatRoomDao().upsert(
            ChatRoom(
                id = chatRoomId,
                userPublicKey = newMember,
                subject = "#admins",
                description = null,
                mlsGroupState = null,
            )
        )
    }

    /** A room with one artifact, one chapter, two chunks and one translation. */
    private suspend fun seedSignedWork() {
        seedRoom()

        val dialect = apply(
            DialectEvent.build(name = "isiZulu", country = "ZA", language = "zu", createdAt = 1_700_000_000L)
        )

        val artifact = apply(
            ArtifactEvent.build(
                name = "In Detention",
                url = "https://example.com/in-detention",
                visibility = "private",
                license = "cc",
                dialectId = dialect.id,
                versionLabel = "1.0",
                createdAt = 1_700_000_010L
            )
        )

        // The second item of the artifact's own batch, built the way the batch
        // builds it. It used to be derived on arrival, which is why the archive
        // once could not carry it: a derived row has no signature to check.
        val version = apply(ArtifactVersionEvent.initialVersionOf(artifact).single())

        val chapter = apply(
            ChapterEvent.build(
                artifactVersionId = version.id,
                name = "Chapter One",
                originalText = "He was a man of parts. He had many.",
                index = 0,
                wordCount = 8,
                characterCount = 35,
                createdAt = 1_700_000_020L
            )
        )

        val chunk = apply(
            ChunkEvent.build(
                chapterId = chapter.id,
                text = "He was a man of parts.",
                index = 0,
                wordCount = 6,
                characterCount = 22,
                createdAt = 1_700_000_030L
            )
        )
        apply(
            ChunkEvent.build(
                chapterId = chapter.id,
                text = "He had many.",
                index = 1,
                wordCount = 3,
                characterCount = 12,
                createdAt = 1_700_000_031L
            )
        )

        val translationVersion = apply(
            TranslationArtifactVersionEvent.build(
                artifactVersionId = version.id,
                dialectId = dialect.id,
                name = "isiZulu",
                visibility = "private",
                license = "cc",
                createdAt = 1_700_000_040L
            )
        )

        val translationChapter = apply(
            TranslationChapterEvent.build(
                translationArtifactVersionId = translationVersion.id,
                chapterId = chapter.id,
                index = 0,
                createdAt = 1_700_000_050L
            )
        )

        // The translated text, which the group signs now rather than each member
        // saving their own.
        apply(
            TranslationChunkEvent.build(
                translationChapterId = translationChapter.id,
                chunkId = chunk.id,
                index = 0,
                text = "Wayeyindoda enezingxenye.",
                createdAt = 1_700_000_060L
            )
        )
    }

    private suspend fun archivedPayloads(): List<Event> =
        ArchiveManager.assemble(db, chatRoomId, newMember)
            .flatMap { assertNotNull(ArchiveEvent.decodePage(it.content)) }

    @Test
    fun `every payload in an archive is one the room signed`() = runBlocking {
        seedSignedWork()

        val payloads = archivedPayloads()

        assertTrue(payloads.isNotEmpty(), "a room with signed work archives something")
        payloads.forEach {
            assertTrue(
                GroupKeyStateEvent.isSignedByRoom(it, chatRoomId),
                "kind ${it.kind} (${it.id.take(8)}) was archived without a signature the room can be checked for"
            )
        }
    }

    @Test
    fun `an archive holds every kind the group signed, and each of them once`() = runBlocking {
        seedSignedWork()

        val byKind = archivedPayloads().groupBy { it.kind }

        assertEquals(1, byKind[DialectEvent.KIND]?.size)
        assertEquals(1, byKind[ArtifactEvent.KIND]?.size)
        assertEquals(1, byKind[ArtifactVersionEvent.KIND]?.size)
        assertEquals(1, byKind[ChapterEvent.KIND]?.size)
        assertEquals(2, byKind[ChunkEvent.KIND]?.size)
        assertEquals(1, byKind[TranslationArtifactVersionEvent.KIND]?.size)
        assertEquals(1, byKind[TranslationChapterEvent.KIND]?.size)
        assertEquals(1, byKind[TranslationChunkEvent.KIND]?.size)

        assertEquals(ArchiveEvent.ARCHIVABLE_KINDS, byKind.keys)
    }

    @Test
    fun `an archive is in dependency order across its whole length`() = runBlocking {
        seedSignedWork()

        val ranks = archivedPayloads().map { assertNotNull(ArchiveEvent.applyRank(it.kind)) }

        assertEquals(ranks.sorted(), ranks, "an archive out of order is a foreign key violation")
    }

    @Test
    fun `a member's own rumor is not part of the group's record`() = runBlocking {
        seedSignedWork()

        // What `MantraDao.addDialect` writes: the member's key, no signature. It
        // is real work in the room and the group never signed it, so it cannot be
        // handed to somebody who has no way to check it.
        db.mantraDialectDao().upsert(
            MantraDialect(
                id = "f".repeat(64),
                publicKey = newMember,
                name = "seSotho",
                country = "ZA",
                language = "st",
                signature = "",
                chatRoomId = chatRoomId,
                createdAt = Instant.fromEpochSeconds(1_700_000_060L),
            )
        )

        assertEquals(
            2,
            db.mantraDialectDao().getDialectsByChatRoomId(chatRoomId).size,
            "the rumor should be on disk"
        )
        assertEquals(
            1,
            archivedPayloads().count { it.kind == DialectEvent.KIND },
            "the unsigned dialect should not have been archived"
        )
    }

    @Test
    fun `a room with nothing signed archives nothing, and says so rather than failing`() = runBlocking {
        seedRoom()

        assertEquals(emptyList(), ArchiveManager.assemble(db, chatRoomId, newMember))
    }

    @Test
    fun `an archive of one page still says it is one of one`() = runBlocking<Unit> {
        seedSignedWork()

        val pages = ArchiveManager.assemble(db, chatRoomId, newMember)

        assertEquals(1, pages.size, "this much work fits one page")

        val page = ArchiveEvent(
            id = "d".repeat(64),
            pubKey = chatRoomId,
            createdAt = pages.single().createdAt,
            tags = pages.single().tags,
            content = pages.single().content,
            sig = ""
        )

        assertEquals(0, page.page()?.index)
        assertEquals(1, page.page()?.count)
        assertEquals(newMember, page.recipient())
        assertNotNull(page.archiveId())
    }

    @Test
    fun `two archives of the same rows do not share an id`() = runBlocking {
        seedSignedWork()

        fun idOf(template: EventTemplate<ArchiveEvent>) = ArchiveEvent(
            id = "d".repeat(64),
            pubKey = chatRoomId,
            createdAt = template.createdAt,
            tags = template.tags,
            content = template.content,
            sig = ""
        ).archiveId()

        // Two members answering one request is the ordinary case, and their page
        // counts differ whenever their databases do. Sharing an id would let one
        // archive's pages be counted towards the other's total.
        assertTrue(
            idOf(ArchiveManager.assemble(db, chatRoomId, newMember).single()) !=
                idOf(ArchiveManager.assemble(db, chatRoomId, newMember).single())
        )
    }
}
