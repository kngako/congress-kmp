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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import press.mantra.compose.database.MantraDatabase
import press.mantra.compose.database.builder.getRoomDatabase
import press.mantra.compose.database.model.ChatMessage
import press.mantra.compose.database.model.ChatRoom
import press.mantra.compose.database.model.MarmotInnerEvent
import press.mantra.compose.database.model.MarmotKeyPackage
import press.mantra.compose.database.model.NostrEvent
import press.mantra.compose.database.model.Profile
import press.mantra.compose.extensions.toHex
import press.mantra.compose.nostr.archive.ArchiveEvent
import press.mantra.compose.nostr.archive.ArchiveRequestEvent
import press.mantra.compose.nostr.archive.tags.ArchiveIdTag
import press.mantra.compose.nostr.archive.tags.ArchivePageTag
import press.mantra.compose.nostr.archive.tags.ArchiveRecipientTag
import com.vitorpamplona.quartz.marmot.mip02Welcome.WelcomeEvent
import press.mantra.compose.nostr.frost.GroupKeyStateEvent
import press.mantra.compose.nostr.nip30303.ArtifactEvent
import press.mantra.compose.nostr.nip30303.ChapterEvent
import press.mantra.compose.nostr.nip30303.ChunkEvent
import press.mantra.compose.nostr.nip30303.DialectEvent
import press.mantra.compose.nostr.nip30303.TranslationArtifactVersionEvent
import press.mantra.compose.nostr.nip30303.TranslationChapterEvent

/**
 * Two devices, two databases: one that did the work and one that arrived after
 * it, with the pages between them carried by hand.
 *
 * This is the claim the whole feature is: a member who holds no share, took part
 * in no signing session and cannot decrypt a word of the room's history ends up
 * with the same rows as everybody else, and gets there without trusting the
 * member who sent them.
 *
 * The negative cases matter more than the positive one. Verification is what
 * makes an archive safe to accept from anybody, so what has to be proved is that
 * it refuses -- a forged payload beside honest ones, and a genuine key state
 * replayed out of its epoch, which passes every signature check there is and is
 * stopped by nothing but the allowlist.
 */
class ArchiveApplyJvmTest {
    /** The member who did the work. */
    private val sender: MantraDatabase = getRoomDatabase(Room.inMemoryDatabaseBuilder<MantraDatabase>())

    /** The member who arrived afterwards. No share, no sessions, no history. */
    private val receiver: MantraDatabase = getRoomDatabase(Room.inMemoryDatabaseBuilder<MantraDatabase>())

    @AfterTest
    fun closeDbs() {
        sender.close()
        receiver.close()
    }

    private val participants = 3
    private val threshold = 2

    private val keyMaterial: KeyMaterial = Frost.trustedDealerKeygen(
        thresholdSecretKey = PrivateKey(
            ByteVector32("1c0ffee0000000000000000000000000000000000000000000000000000000a1")
        ),
        nParticipants = participants,
        threshold = threshold
    )

    /** A second group entirely: its own key, its own quorum, its own rooms. */
    private val otherKeyMaterial: KeyMaterial = Frost.trustedDealerKeygen(
        thresholdSecretKey = PrivateKey(
            ByteVector32("2bada550000000000000000000000000000000000000000000000000000000b2")
        ),
        nParticipants = participants,
        threshold = threshold
    )

    private val room: SharedKeyDerivation.Derived = SharedKeyDerivation.derive(
        thresholdPublicKey = keyMaterial.thresholdPublicKey.value.toHex(),
        path = SharedKeyDerivation.MARMOT_ADMIN_GROUP_PATH
    )

    private val chatRoomId = room.hex

    private val oldMember = "8".repeat(64)
    private val newMember = "9".repeat(64)

    private fun groupSignature(material: KeyMaterial, eventId: String): String {
        val cache = SharedKeyDerivation.derive(
            thresholdPublicKey = material.thresholdPublicKey.value.toHex(),
            path = SharedKeyDerivation.MARMOT_ADMIN_GROUP_PATH
        ).cache
        val message = ByteVector(eventId.hexToByteArray())
        val signerIds = listOf(0, 1)

        val nonces = signerIds.map { signerId ->
            SecretNonce.generate(
                sessionRandom = ByteVector32("a".repeat(63) + "${signerId + 1}"),
                secretShare = material.secretShares[signerId],
                publicShare = material.publicShares[signerId],
                tweakedThresholdPublicKey = cache.tweakedPublicKey,
                message = message,
                extraInput = null
            )
        }

        val signingSession = Session.create(
            aggregatedNonce = IndividualNonce.aggregate(nonces.map { it.second }).right!!,
            signerIds = signerIds.map { it.toUInt() },
            signerPublicShares = signerIds.map { material.publicShares[it] },
            nParticipants = participants,
            threshold = threshold,
            tweakCache = cache,
            message = message
        )

        val partials = signerIds.mapIndexed { position, signerId ->
            signingSession.sign(
                nonces[position].first,
                material.secretShares[signerId],
                signerId.toUInt()
            ).right!!
        }

        return signingSession.aggregateSigs(partials).right!!.toByteArray().toHex()
    }

    /** A template as a finished session leaves it, authored by [material]'s room. */
    private fun signedBy(material: KeyMaterial, template: EventTemplate<*>): Event {
        val author = SharedKeyDerivation.derive(
            thresholdPublicKey = material.thresholdPublicKey.value.toHex(),
            path = SharedKeyDerivation.MARMOT_ADMIN_GROUP_PATH
        ).hex

        val id = EventHasher.hashId(
            pubKey = author,
            createdAt = template.createdAt,
            kind = template.kind,
            tags = template.tags,
            content = template.content
        )

        return Event(
            id = id,
            pubKey = author,
            createdAt = template.createdAt,
            kind = template.kind,
            tags = template.tags,
            content = template.content,
            sig = groupSignature(material, id)
        )
    }

    private fun signed(template: EventTemplate<*>) = signedBy(keyMaterial, template)

    private suspend fun seedRoom(db: MantraDatabase, user: String) {
        val nostrEventId = "c".repeat(63) + if (user == newMember) "1" else "2"
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
        db.profileDao().upsert(Profile(publicKey = user, userName = "member", nostrEventId = nostrEventId))
        db.chatRoomDao().upsert(
            ChatRoom(
                id = chatRoomId,
                userPublicKey = user,
                subject = "#admins",
                description = null,
                mlsGroupState = null,
            )
        )
    }

    private suspend fun apply(db: MantraDatabase, event: Event) {
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
    }

    /** The sender's room, with a full artifact and one translation scaffolded. */
    private suspend fun seedSenderWork() {
        seedRoom(sender, oldMember)

        val dialect = signed(
            DialectEvent.build(name = "isiZulu", country = "ZA", language = "zu", createdAt = 1_700_000_000L)
        ).also { apply(sender, it) }

        val artifact = signed(
            ArtifactEvent.build(
                name = "In Detention",
                url = "https://example.com/in-detention",
                visibility = "private",
                license = "cc",
                dialectId = dialect.id,
                versionLabel = "1.0",
                createdAt = 1_700_000_010L
            )
        ).also { apply(sender, it) }

        val version = assertNotNull(
            sender.mantraArtifactVersionDao().getArtifactVersionsByArtifactId(artifact.id).firstOrNull()
        )

        val chapter = signed(
            ChapterEvent.build(
                artifactVersionId = version.id,
                name = "Chapter One",
                originalText = "He was a man of parts. He had many.",
                index = 0,
                wordCount = 8,
                characterCount = 35,
                createdAt = 1_700_000_020L
            )
        ).also { apply(sender, it) }

        apply(
            sender,
            signed(
                ChunkEvent.build(
                    chapterId = chapter.id,
                    text = "He was a man of parts.",
                    index = 0,
                    wordCount = 6,
                    characterCount = 22,
                    createdAt = 1_700_000_030L
                )
            )
        )
        apply(
            sender,
            signed(
                ChunkEvent.build(
                    chapterId = chapter.id,
                    text = "He had many.",
                    index = 1,
                    wordCount = 3,
                    characterCount = 12,
                    createdAt = 1_700_000_031L
                )
            )
        )

        val translationVersion = signed(
            TranslationArtifactVersionEvent.build(
                artifactVersionId = version.id,
                dialectId = dialect.id,
                name = "isiZulu",
                visibility = "private",
                license = "cc",
                createdAt = 1_700_000_040L
            )
        ).also { apply(sender, it) }

        apply(
            sender,
            signed(
                TranslationChapterEvent.build(
                    translationArtifactVersionId = translationVersion.id,
                    chapterId = chapter.id,
                    index = 0,
                    createdAt = 1_700_000_050L
                )
            )
        )
    }

    /**
     * An archive page as it reaches a device: stored as an inner event first,
     * exactly as the inbound path stores every payload it decrypts before
     * dispatching on kind, then handed to the manager.
     */
    private suspend fun deliver(
        template: EventTemplate<ArchiveEvent>,
        to: String = newMember,
        from: String = oldMember,
    ): ArchiveManager.Outcome {
        val page = Event(
            id = EventHasher.hashId(
                pubKey = from,
                createdAt = template.createdAt,
                kind = template.kind,
                tags = template.tags,
                content = template.content
            ),
            pubKey = from,
            createdAt = template.createdAt,
            kind = template.kind,
            tags = template.tags,
            // An archive page is a member's own rumor. What carries the group's
            // word is each payload's signature, not the envelope's.
            content = template.content,
            sig = ""
        )

        receiver.marmotInnerEventDao().upsert(
            MarmotInnerEvent(
                id = page.id,
                publicKey = page.pubKey,
                kind = page.kind,
                createdAt = Instant.fromEpochSeconds(page.createdAt),
                tags = page.tags,
                content = page.content,
                chatRoomId = chatRoomId,
            )
        )

        return ArchiveManager.receive(receiver, chatRoomId, to, page)
    }

    private suspend fun senderArchive() = ArchiveManager.assemble(sender, chatRoomId, newMember)

    private suspend fun rowCounts(db: MantraDatabase): Map<String, Int> {
        val dialects = db.mantraDialectDao().getDialectsByChatRoomId(chatRoomId)
        val artifacts = db.mantraArtifactDao().getArtifactsByChatRoomId(chatRoomId)
        val versions = artifacts.flatMap {
            db.mantraArtifactVersionDao().getArtifactVersionsByArtifactId(it.id)
        }
        val chapters = versions.flatMap { db.mantraChapterDao().getChaptersByArtifactVersionId(it.id) }
        val chunks = chapters.flatMap { db.mantraChunkDao().getChunksByChapterId(it.id) }
        val translationVersions = versions.flatMap {
            db.mantraTranslationArtifactVersionDao().getTranslationsByArtifactVersionId(it.id)
        }
        val translationChapters = translationVersions.flatMap {
            db.mantraTranslationChapterDao()
                .getTranslationChaptersByTranslationArtifactVersionId(it.id)
        }

        return mapOf(
            "dialects" to dialects.size,
            "artifacts" to artifacts.size,
            "versions" to versions.size,
            "chapters" to chapters.size,
            "chunks" to chunks.size,
            "translationVersions" to translationVersions.size,
            "translationChapters" to translationChapters.size,
        )
    }

    @Test
    fun `a member who was never there ends up holding what the group signed`() = runBlocking {
        seedSenderWork()
        seedRoom(receiver, newMember)

        assertEquals(
            mapOf(
                "dialects" to 0, "artifacts" to 0, "versions" to 0, "chapters" to 0,
                "chunks" to 0, "translationVersions" to 0, "translationChapters" to 0,
            ),
            rowCounts(receiver),
            "the new member starts with nothing, which is the whole problem"
        )

        senderArchive().forEach { deliver(it) }

        assertEquals(rowCounts(sender), rowCounts(receiver))

        // Not merely the same shape: the same rows, with the group's signature on
        // them. An archive that produced look-alike rows authored by the sender
        // would pass a count and fail the only thing that matters.
        val theirs = receiver.mantraArtifactDao().getArtifactsByChatRoomId(chatRoomId).single()
        val ours = sender.mantraArtifactDao().getArtifactsByChatRoomId(chatRoomId).single()
        assertEquals(ours.id, theirs.id)
        assertEquals(ours.signature, theirs.signature)
        assertEquals(chatRoomId, theirs.publicKey)
    }

    @Test
    fun `an archive files no chat lines`() = runBlocking {
        seedSenderWork()
        seedRoom(receiver, newMember)

        senderArchive().forEach { deliver(it) }

        assertEquals(
            emptyList(),
            receiver.chatMessageDao().getChatMessagesByChatRoomId(chatRoomId)
                .map { it.chatMessage.messageType }
                .filterNot { it in ChatMessage.ARCHIVE_TYPES },
            "the archive restores the work; the conversation is forward secret and stays gone"
        )
    }

    @Test
    fun `pages arriving out of order still converge`() = runBlocking {
        seedSenderWork()
        seedRoom(receiver, newMember)

        // One page per payload, delivered backwards -- a translation chapter
        // before the chapter it names, chunks before their chapter, the artifact
        // last. Relays give no ordering guarantee, so this is the ordinary case
        // rather than the adversarial one.
        val payloads = senderArchive()
            .flatMap { assertNotNull(ArchiveEvent.decodePage(it.content)) }
        val archiveId = "e".repeat(64)

        val outcomes = payloads.reversed().mapIndexed { index, payload ->
            deliver(
                ArchiveEvent.build(
                    payloads = listOf(payload),
                    archiveId = archiveId,
                    index = index,
                    count = payloads.size,
                    recipient = newMember,
                    createdAt = 1_700_000_100L + index,
                )
            )
        }

        // Without this the test could pass for the wrong reason -- if nothing
        // ever failed to apply, the pages were not really out of order and the
        // sweep was never the thing that saved them.
        assertTrue(
            outcomes.any { it.failed > 0 },
            "delivering backwards should have left payloads waiting on rows they depend on"
        )
        assertEquals(
            0,
            outcomes.last().failed,
            "the last page completes the archive, so the pass it triggers should leave nothing"
        )

        assertEquals(rowCounts(sender), rowCounts(receiver))
    }

    @Test
    fun `one forged payload costs itself and nothing else`() = runBlocking {
        seedSenderWork()
        seedRoom(receiver, newMember)

        val honest = signed(
            DialectEvent.build(name = "isiZulu", country = "ZA", language = "zu", createdAt = 1_700_000_000L)
        )

        // Four ways to be a dishonest member, in one page beside one honest
        // dialect. Every one of them is well-formed and would file a row without
        // the check.
        val forgeries = listOf(
            // The room's id as author -- everybody knows it -- and a made-up
            // signature.
            Event(
                id = EventHasher.hashId(
                    pubKey = chatRoomId, createdAt = 1_700_000_001L, kind = DialectEvent.KIND,
                    tags = honest.tags, content = "forged"
                ),
                pubKey = chatRoomId, createdAt = 1_700_000_001L, kind = DialectEvent.KIND,
                tags = honest.tags, content = "forged", sig = "9".repeat(128)
            ),
            // A real quorum, of another group.
            signedBy(
                otherKeyMaterial,
                DialectEvent.build(name = "seSotho", country = "ZA", language = "st", createdAt = 1_700_000_002L)
            ),
            // Edited after the group signed it.
            Event(
                id = honest.id, pubKey = honest.pubKey, createdAt = honest.createdAt,
                kind = honest.kind,
                tags = arrayOf(arrayOf("alt", "Dialect"), arrayOf("name", "tampered")),
                content = honest.content, sig = honest.sig
            ),
            // A member's own rumor, which is what everything on the wire looks
            // like today.
            Event(
                id = EventHasher.hashId(
                    pubKey = oldMember, createdAt = 1_700_000_003L, kind = DialectEvent.KIND,
                    tags = honest.tags, content = "unsigned"
                ),
                pubKey = oldMember, createdAt = 1_700_000_003L, kind = DialectEvent.KIND,
                tags = honest.tags, content = "unsigned", sig = ""
            ),
        )

        val outcome = deliver(
            ArchiveEvent.build(
                payloads = forgeries + honest,
                archiveId = "f".repeat(64),
                index = 0,
                count = 1,
                recipient = newMember,
                createdAt = 1_700_000_100L,
            )
        )

        assertEquals(4, outcome.skipped)
        assertEquals(1, outcome.applied)

        val dialects = receiver.mantraDialectDao().getDialectsByChatRoomId(chatRoomId)
        assertEquals(1, dialects.size, "exactly the honest one")
        assertEquals(honest.id, dialects.single().id)
    }

    @Test
    fun `a key state the room really signed cannot be replayed through an archive`() = runBlocking {
        seedSenderWork()
        seedRoom(receiver, newMember)

        // Signed by the room, verifying perfectly, and refused: the allowlist is
        // the only thing between "the group signed this once" and "any member may
        // re-point what this room signs with, at any time, forever".
        val keyState = signed(
            EventTemplate<Event>(
                createdAt = 1_700_000_060L,
                kind = GroupKeyStateEvent.KIND,
                tags = GroupKeyStateEvent.assembleTags(
                    chatRoomId = chatRoomId,
                    dkgSessionId = "somebody-elses-ceremony",
                ),
                content = keyMaterial.thresholdPublicKey.value.toHex(),
            )
        )
        assertTrue(
            GroupKeyStateEvent.isSignedByRoom(keyState, chatRoomId),
            "the point of this test is that verification passes"
        )

        // Built by hand: ArchiveEvent.build refuses this kind, which is the
        // outbound half of the same rule. Only a hand-rolled page gets this far.
        val page = EventTemplate<ArchiveEvent>(
            createdAt = 1_700_000_100L,
            kind = ArchiveEvent.KIND,
            tags = arrayOf(
                arrayOf("alt", ArchiveEvent.ALT_DESCRIPTION),
                ArchiveIdTag.assemble("a".repeat(64)),
                ArchivePageTag.assemble(0, 1),
                ArchiveRecipientTag.assemble(newMember),
            ),
            content = ArchiveEvent.encodePage(listOf(keyState)),
        )

        val outcome = deliver(page)

        assertEquals(1, outcome.skipped)
        assertEquals(0, outcome.applied)
        assertNull(receiver.groupKeyStateDao().getByChatRoomId(chatRoomId))
    }

    @Test
    fun `a member the archive is not addressed to applies none of it`() = runBlocking {
        seedSenderWork()
        seedRoom(receiver, newMember)

        val outcome = senderArchive().map { deliver(it, to = "7".repeat(64)) }

        assertEquals(0, outcome.sumOf { it.applied })
        assertEquals(
            mapOf(
                "dialects" to 0, "artifacts" to 0, "versions" to 0, "chapters" to 0,
                "chunks" to 0, "translationVersions" to 0, "translationChapters" to 0,
            ),
            rowCounts(receiver),
            "a bystander already holds the work; re-applying would only rewrite its provenance"
        )
    }

    @Test
    fun `applying the same archive twice changes nothing the second time`() = runBlocking {
        seedSenderWork()
        seedRoom(receiver, newMember)

        val archive = senderArchive()
        archive.forEach { deliver(it) }
        val afterFirst = rowCounts(receiver)

        archive.forEach { deliver(it) }

        assertEquals(afterFirst, rowCounts(receiver))
        assertEquals(
            emptyList(),
            receiver.chatMessageDao().getChatMessagesByChatRoomId(chatRoomId)
                .map { it.chatMessage.messageType }
                .filterNot { it in ChatMessage.ARCHIVE_TYPES },
        )
    }

    // ---- Asking for one --------------------------------------------------

    private suspend fun queued(db: MantraDatabase, kind: Int) = db.marmotInnerEventDao()
        .getByChatRoomAndKinds(chatRoomId, listOf(kind))
        .filter { it.marmotGroupEventId == null }

    @Test
    fun `a device holding none of the room's work asks for it`() = runBlocking {
        seedRoom(receiver, newMember)

        assertTrue(ArchiveManager.requestIfEmpty(receiver, chatRoomId, newMember))

        // Queued with no chat line, the way a signing message travels. Broadcast
        // does not depend on one, and a row of envelopes in the transcript is not
        // what an archive should leave behind.
        assertEquals(1, queued(receiver, ArchiveRequestEvent.KIND).size)

        // One line saying so, and nothing else. Asking is a thing this device did
        // and the room would otherwise sit empty with no explanation.
        assertEquals(
            listOf(ChatMessage.TYPE_ARCHIVE_REQUESTED),
            receiver.chatMessageDao().getChatMessagesByChatRoomId(chatRoomId)
                .map { it.chatMessage.messageType }
        )
    }

    @Test
    fun `a device does not ask twice while an answer is in flight`() = runBlocking {
        seedRoom(receiver, newMember)

        assertTrue(ArchiveManager.requestIfEmpty(receiver, chatRoomId, newMember))
        assertFalse(ArchiveManager.requestIfEmpty(receiver, chatRoomId, newMember))

        assertEquals(1, queued(receiver, ArchiveRequestEvent.KIND).size)
    }

    @Test
    fun `a device holding the work does not ask for it`() = runBlocking {
        seedSenderWork()

        assertFalse(ArchiveManager.requestIfEmpty(sender, chatRoomId, oldMember))
        assertEquals(0, queued(sender, ArchiveRequestEvent.KIND).size)
    }

    @Test
    fun `an answered request lets the room ask again`() = runBlocking {
        seedSenderWork()
        seedRoom(receiver, newMember)

        ArchiveManager.requestIfEmpty(receiver, chatRoomId, newMember)
        assertNotNull(receiver.chatRoomDao().findChatRoomById(chatRoomId)?.chatRoom?.archiveRequestedAt)

        senderArchive().forEach { deliver(it) }

        // Cleared on anything applied rather than on the sender claiming the
        // archive was complete. A page count is the sender's word about the
        // transfer, not about the group's record, so a member who left work out
        // must not get the last word on whether to ask again.
        assertNull(receiver.chatRoomDao().findChatRoomById(chatRoomId)?.chatRoom?.archiveRequestedAt)
    }

    @Test
    fun `answering a request queues the archive for whoever asked`() = runBlocking {
        seedSenderWork()

        val pages = ArchiveManager.sendTo(sender, chatRoomId, oldMember, recipient = newMember)

        assertEquals(1, pages)

        val queuedPage = queued(sender, ArchiveEvent.KIND).single()
        assertEquals(oldMember, queuedPage.publicKey, "the page is the answering member's own rumor")

        val page = ArchiveEvent(
            id = queuedPage.id,
            pubKey = queuedPage.publicKey,
            createdAt = queuedPage.createdAt.epochSeconds,
            tags = queuedPage.tags,
            content = queuedPage.content,
            sig = "",
        )
        assertEquals(newMember, page.recipient())
        assertEquals(ArchiveEvent.ARCHIVABLE_KINDS, page.payloads()?.map { it.kind }?.toSet())

        // One line about the archive, and no line per page.
        assertEquals(
            listOf(ChatMessage.TYPE_ARCHIVE_SENT),
            sender.chatMessageDao().getChatMessagesByChatRoomId(chatRoomId)
                .map { it.chatMessage.messageType }
        )
    }

    @Test
    fun `a member with nothing signed answers nothing rather than an empty archive`() = runBlocking {
        seedRoom(receiver, newMember)

        assertEquals(0, ArchiveManager.sendTo(receiver, chatRoomId, newMember, recipient = oldMember))
        assertEquals(0, queued(receiver, ArchiveEvent.KIND).size)
    }

    @Test
    fun `a member does not answer their own request`() = runBlocking {
        seedSenderWork()

        assertEquals(0, ArchiveManager.sendTo(sender, chatRoomId, oldMember, recipient = oldMember))
        assertEquals(0, queued(sender, ArchiveEvent.KIND).size)
    }

    @Test
    fun `asking and answering completes the round trip`() = runBlocking {
        seedSenderWork()
        seedRoom(receiver, newMember)

        // The joiner asks.
        assertTrue(ArchiveManager.requestIfEmpty(receiver, chatRoomId, newMember))

        // A member holding the work answers it, addressed to whoever asked.
        ArchiveManager.sendTo(sender, chatRoomId, oldMember, recipient = newMember)

        // The pages reach the joiner, who applies them.
        queued(sender, ArchiveEvent.KIND).forEach { queuedPage ->
            deliver(
                EventTemplate(
                    createdAt = queuedPage.createdAt.epochSeconds,
                    kind = queuedPage.kind,
                    tags = queuedPage.tags,
                    content = queuedPage.content,
                )
            )
        }

        assertEquals(rowCounts(sender), rowCounts(receiver))
        assertNull(receiver.chatRoomDao().findChatRoomById(chatRoomId)?.chatRoom?.archiveRequestedAt)
    }

    // ---- The push behind a Welcome ---------------------------------------

    /**
     * The invitee's key package, which is how `deliveryWelcome` learns who it is
     * addressing. The bytes are never decoded on this path.
     */
    private suspend fun seedInviteeKeyPackage(): String {
        val keyPackageId = "1".repeat(64)
        val nostrEventId = "2".repeat(64)

        sender.nostrEventDao().upsert(
            NostrEvent(
                id = nostrEventId,
                pubKey = newMember,
                kind = 0,
                tags = emptyArray(),
                content = "{}",
                sig = "0".repeat(128),
            )
        )
        sender.profileDao().upsert(
            Profile(publicKey = newMember, userName = "invitee", nostrEventId = nostrEventId)
        )
        sender.marmotKeyPackageDao().upsert(
            MarmotKeyPackage(
                id = keyPackageId,
                publicKey = newMember,
                tlsEncodedMarmotKeyPackage = ByteArray(0),
            )
        )

        return keyPackageId
    }

    @Test
    fun `inviting a member offers them the group's work alongside the welcome`() = runBlocking {
        seedSenderWork()

        sender.marmotOutboundDao().deliveryWelcome(
            nostrGroupId = chatRoomId,
            userPublicKey = oldMember,
            welcomeBytes = ByteArray(8),
            peerKeyPackageEventId = seedInviteeKeyPackage(),
            relays = listOf("wss://relay.example.com"),
            createdAt = Clock.System.now(),
        )

        val page = queued(sender, ArchiveEvent.KIND).single()

        assertEquals(
            newMember,
            ArchiveEvent(
                id = page.id,
                pubKey = page.publicKey,
                createdAt = page.createdAt.epochSeconds,
                tags = page.tags,
                content = page.content,
                sig = "",
            ).recipient()
        )
    }

    @Test
    fun `inviting into a room with nothing signed offers nothing, and still invites`() = runBlocking {
        seedRoom(sender, oldMember)

        sender.marmotOutboundDao().deliveryWelcome(
            nostrGroupId = chatRoomId,
            userPublicKey = oldMember,
            welcomeBytes = ByteArray(8),
            peerKeyPackageEventId = seedInviteeKeyPackage(),
            relays = listOf("wss://relay.example.com"),
            createdAt = Clock.System.now(),
        )

        assertEquals(0, queued(sender, ArchiveEvent.KIND).size)
        // The Welcome itself still went out. A push that has nothing to say is
        // not a failed invite, and nothing about the archive may report one:
        // the invitee asks for themselves on their first open regardless.
        assertEquals(
            1,
            sender.giftWrapPayloadDao()
                .getByChatRoomAndKinds(chatRoomId, listOf(WelcomeEvent.KIND))
                .size
        )
    }

    @Test
    fun `an answered catch-up leaves one line, whatever it took to deliver`() = runBlocking {
        seedSenderWork()
        seedRoom(receiver, newMember)

        ArchiveManager.requestIfEmpty(receiver, chatRoomId, newMember)

        // Delivered a page at a time and out of order, so the sweep runs several
        // times over several pages. The transcript is not a log of that.
        val payloads = senderArchive()
            .flatMap { assertNotNull(ArchiveEvent.decodePage(it.content)) }

        payloads.reversed().forEachIndexed { index, payload ->
            deliver(
                ArchiveEvent.build(
                    payloads = listOf(payload),
                    archiveId = "e".repeat(64),
                    index = index,
                    count = payloads.size,
                    recipient = newMember,
                    createdAt = 1_700_000_100L + index,
                )
            )
        }

        assertEquals(
            listOf(ChatMessage.TYPE_ARCHIVE_REQUESTED, ChatMessage.TYPE_ARCHIVE_RECEIVED),
            receiver.chatMessageDao().getChatMessagesByChatRoomId(chatRoomId)
                .map { it.chatMessage.messageType }
        )
    }

    @Test
    fun `a push nobody asked for says nothing in the transcript`() = runBlocking {
        seedSenderWork()
        seedRoom(receiver, newMember)

        // The invite-time push lands before the member has opened the room, so
        // "caught up on work you have not seen yet" is a line about nothing.
        senderArchive().forEach { deliver(it) }

        assertEquals(
            emptyList(),
            receiver.chatMessageDao().getChatMessagesByChatRoomId(chatRoomId)
                .map { it.chatMessage.messageType }
        )
    }
}
