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
import press.mantra.compose.database.model.GroupSignedEvent
import press.mantra.compose.database.model.MantraDialect
import press.mantra.compose.database.model.NostrEvent
import press.mantra.compose.database.model.Profile
import press.mantra.compose.extensions.toHex
import press.mantra.compose.nostr.archive.ArchiveEvent
import press.mantra.compose.nostr.frost.GroupKeyStateEvent
import press.mantra.compose.nostr.nip30303.SubmissionEvent
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

    /**
     * Every event `apply` has been given, in the order it was given them.
     *
     * A test that wants the room's work on file as events too replays this
     * through [record], which is the pair `FrostSigningManager.complete` writes
     * in one pass. Kept here rather than returned by `seedSignedWork` so the
     * seeding reads the same in both worlds.
     */
    private val applied = mutableListOf<Event>()

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

        applied += event

        return event
    }

    /** What `FrostSigningManager.recordSignedEvents` does, through the same table. */
    private suspend fun record(vararg events: Event) {
        db.groupSignedEventDao().recordAll(
            events.map {
                GroupSignedEvent.fromEvent(
                    event = it,
                    chatRoomId = chatRoomId,
                    derivationPath = SharedKeyDerivation.formatPath(),
                    frostSigningSessionId = "session-1",
                )
            }
        )
    }

    /** The whole seeded batch on file as events, as a device that signed it holds it. */
    private suspend fun recordEverythingApplied() = record(*applied.toTypedArray())

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

    // ---- Reading from the record rather than rebuilding -------------------

    /**
     * The events, not a reconstruction of them.
     *
     * Byte-for-byte against what was signed, which is the assertion the whole
     * rebuild machinery exists to approximate. Here it is exact by construction:
     * nothing is rebuilt, so there is no tag order to get wrong and no field a
     * row could have failed to keep.
     */
    @Test
    fun `an archive carries the group's events as the group signed them`() = runBlocking {
        seedSignedWork()
        recordEverythingApplied()

        val payloads = archivedPayloads().associateBy { it.id }

        assertEquals(
            applied.map { it.id }.toSet(),
            payloads.keys,
            "everything the group signed here should be in the archive"
        )
        applied.forEach { event ->
            assertEquals(
                event.toJson(),
                assertNotNull(payloads[event.id]).toJson(),
                "kind ${event.kind} did not travel as the group signed it"
            )
        }
    }

    /**
     * Both sources, one archive, and nothing counted twice.
     *
     * The state a device upgrading mid-life is in: older work only the rows
     * remember, newer work on file as events. Neither half is the archive on its
     * own, and an event held both ways is still one event.
     */
    @Test
    fun `work held both ways travels exactly once`() = runBlocking {
        seedSignedWork()

        // Half the batch on file, the other half only as rows -- which is what
        // a room that was upgraded partway through its life looks like.
        record(*applied.take(applied.size / 2).toTypedArray())

        val payloads = archivedPayloads()

        assertEquals(
            applied.map { it.id }.toSet(),
            payloads.map { it.id }.toSet(),
            "the two sources together should still be the whole record"
        )
        assertEquals(
            payloads.size,
            payloads.map { it.id }.toSet().size,
            "an event on file and rebuildable is one event, not two"
        )
    }

    /**
     * The failure this source made possible, and the allowlist that stops it.
     *
     * A key state is group-signed and verifies perfectly, and every room signs
     * one as its first act -- so it is on file in every room that has ever
     * signed anything. The rebuild could not reach one because no `Mantra*` row
     * holds it; the record holds every kind. Without the filter on the way out
     * `ArchiveEvent.build` refuses the page and the room's whole archive fails
     * on the one event every room has.
     */
    @Test
    fun `a key state the room really signed is never archived`() = runBlocking {
        seedSignedWork()
        recordEverythingApplied()

        val keyState = signed(
            EventTemplate<Event>(
                createdAt = 1_700_000_070L,
                kind = GroupKeyStateEvent.KIND,
                tags = GroupKeyStateEvent.assembleTags(
                    chatRoomId = chatRoomId,
                    dkgSessionId = "the-ceremony",
                ),
                content = keyMaterial.thresholdPublicKey.value.toHex(),
            )
        )
        record(keyState)
        assertTrue(
            GroupKeyStateEvent.isSignedByRoom(keyState, chatRoomId),
            "the point of this test is that verification passes"
        )

        val payloads = archivedPayloads()

        assertTrue(payloads.isNotEmpty(), "the rest of the room's work still archives")
        assertEquals(
            ArchiveEvent.ARCHIVABLE_KINDS,
            payloads.map { it.kind }.toSet(),
            "an archive carries the document kinds and nothing else"
        )
        assertTrue(payloads.none { it.id == keyState.id })
    }

    /** The same rule, for a kind that is merely not on the list rather than dangerous. */
    @Test
    fun `a signed kind the archive has no arm for is left behind`() = runBlocking {
        seedSignedWork()
        recordEverythingApplied()

        record(
            signed(
                EventTemplate<Event>(
                    createdAt = 1_700_000_080L,
                    kind = SubmissionEvent.KIND,
                    tags = emptyArray(),
                    content = "{}",
                )
            )
        )

        assertEquals(
            ArchiveEvent.ARCHIVABLE_KINDS,
            archivedPayloads().map { it.kind }.toSet()
        )
    }

    /**
     * The case the rebuild cannot reach at all, which is why the record is the
     * source and not merely a faster one.
     *
     * `MantraArtifact` does not hold the version label it was signed with --
     * `fromArtifactEvent` drops it -- so `toArtifactEvent` has to go and find it
     * on the initial version, and an artifact whose version row is missing is one
     * the rebuild logs and leaves out. On file as an event there is nothing to
     * find: the label never left.
     */
    @Test
    fun `an artifact the rebuild has to leave out still archives from the record`() = runBlocking {
        seedRoom()

        val dialect = apply(
            DialectEvent.build(name = "isiZulu", country = "ZA", language = "zu", createdAt = 1_700_000_000L)
        )

        // Applied without the second item of its batch, so no version row carries
        // the label back. A device that was offline for half a batch, or one that
        // failed to apply the version, is in exactly this state.
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

        assertEquals(
            emptyList(),
            db.mantraArtifactVersionDao().getArtifactVersionsByArtifactId(artifact.id),
            "this test is only about an artifact whose initial version is missing"
        )

        // Rows only: the rebuild reaches the dialect and gives up on the artifact.
        assertEquals(
            listOf(dialect.id),
            archivedPayloads().map { it.id },
        )

        record(dialect, artifact)

        assertEquals(
            setOf(dialect.id, artifact.id),
            archivedPayloads().map { it.id }.toSet(),
            "the record holds the whole event, version label and all"
        )
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

    // ---- Supersession ----------------------------------------------------
    //
    // The record keeps every event the group signed; the rows keep one
    // translation per passage. Reading the archive from the record is what makes
    // the difference visible, and these are what keep the two sides agreeing
    // about which translation stands.

    /** The passage the seed already translated, resolved back out of the rows. */
    private suspend fun seededPassage(): Pair<String, String> {
        val artifactId = db.mantraArtifactDao().getArtifactsByChatRoomId(chatRoomId).single().id
        val versionId = db.mantraArtifactVersionDao()
            .getArtifactVersionsByArtifactId(artifactId).single().id
        val chapterId = db.mantraChapterDao().getChaptersByArtifactVersionId(versionId).single().id
        val chunkId = db.mantraChunkDao().getChunksByChapterId(chapterId).first().id
        val translationChapterId = db.mantraTranslationChapterDao()
            .getTranslationChaptersByTranslationArtifactVersionId(
                db.mantraTranslationArtifactVersionDao()
                    .getTranslationsByArtifactVersionId(versionId).single().id
            ).single().id

        return translationChapterId to chunkId
    }

    private suspend fun archivedTranslations(): List<Event> =
        ArchiveManager.assemble(db, chatRoomId, newMember)
            .flatMap { assertNotNull(ArchiveEvent.decodePage(it.content)) }
            .filter { it.kind == TranslationChunkEvent.KIND }

    @Test
    fun `a retranslated passage archives once, not once per draft`() = runBlocking {
        seedSignedWork()
        val (translationChapterId, chunkId) = seededPassage()

        val current = apply(
            TranslationChunkEvent.build(
                translationChapterId = translationChapterId,
                chunkId = chunkId,
                index = 0,
                text = "A better rendering.",
                createdAt = 1_700_000_070L
            )
        )
        recordEverythingApplied()

        // One row, because the applying arm superseded the first. Two events,
        // because the record keeps what the group signed and does not pretend a
        // signature was never made.
        assertEquals(
            1,
            db.mantraTranslationChunkDao()
                .getTranslationChunksByTranslationChapterId(translationChapterId).size
        )
        assertEquals(
            2,
            db.groupSignedEventDao().getByChatRoomIdAndKind(chatRoomId, TranslationChunkEvent.KIND).size
        )

        // And one payload. Otherwise every draft a group ever signed travels in
        // every archive it ever sends, for as long as the room exists.
        assertEquals(listOf(current.id), archivedTranslations().map { it.id })
    }

    @Test
    fun `the archive picks the translation the recipient would keep`() = runBlocking {
        seedSignedWork()
        val (translationChapterId, chunkId) = seededPassage()

        // Two translations signed in the same second, which the applying arm
        // settles by id. The archive has to settle it the same way or it ships
        // one as current while the recipient keeps the other -- and both being
        // validly signed, nothing downstream would notice they disagree.
        val sameSecond = 1_700_000_070L
        val rivals = listOf("Rendering one.", "Rendering two.", "Rendering three.")
            .map { text ->
                apply(
                    TranslationChunkEvent.build(
                        translationChapterId = translationChapterId,
                        chunkId = chunkId,
                        index = 0,
                        text = text,
                        createdAt = sameSecond
                    )
                )
            }
        recordEverythingApplied()

        val kept = db.mantraTranslationChunkDao()
            .getTranslationChunksByTranslationChapterId(translationChapterId).single()

        assertEquals(rivals.maxOf { it.id }, kept.id, "the row keeps the highest id of the second")
        assertEquals(listOf(kept.id), archivedTranslations().map { it.id })
    }

    @Test
    fun `two passages each keep their own current translation`() = runBlocking {
        seedSignedWork()
        val (translationChapterId, firstChunkId) = seededPassage()

        val chapterId = db.mantraChapterDao()
            .getChaptersByArtifactVersionId(
                db.mantraArtifactVersionDao()
                    .getArtifactVersionsByArtifactId(
                        db.mantraArtifactDao().getArtifactsByChatRoomId(chatRoomId).single().id
                    ).single().id
            ).single().id
        val secondChunkId = db.mantraChunkDao().getChunksByChapterId(chapterId)
            .first { it.id != firstChunkId }.id

        // Grouping is per passage, so retranslating one must not take the other
        // with it -- the bug a naive "newest translation wins" would have.
        val retranslated = apply(
            TranslationChunkEvent.build(
                translationChapterId = translationChapterId,
                chunkId = firstChunkId,
                index = 0,
                text = "A better rendering.",
                createdAt = 1_700_000_070L
            )
        )
        val other = apply(
            TranslationChunkEvent.build(
                translationChapterId = translationChapterId,
                chunkId = secondChunkId,
                index = 1,
                text = "Wayenezinye eziningi.",
                createdAt = 1_700_000_065L
            )
        )
        recordEverythingApplied()

        assertEquals(
            setOf(retranslated.id, other.id),
            archivedTranslations().map { it.id }.toSet()
        )
    }
}
