package press.mantra.compose.managers

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import com.vitorpamplona.quartz.nip01Core.crypto.Nip01Crypto
import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.PrivateKey
import fr.acinq.bitcoin.crypto.frost.Frost
import fr.acinq.bitcoin.crypto.frost.IndividualNonce
import fr.acinq.bitcoin.crypto.frost.KeyMaterial
import fr.acinq.bitcoin.crypto.frost.SecretNonce
import fr.acinq.bitcoin.crypto.frost.Session
import fr.acinq.bitcoin.crypto.frost.TweakCache
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import press.mantra.compose.database.model.FrostSigningSession
import press.mantra.compose.database.model.MantraChapter
import press.mantra.compose.database.model.MantraChunk
import press.mantra.compose.extensions.toHex
import press.mantra.compose.nostr.nip30303.ChapterEvent

/**
 * A chapter the group signed, from proposal to rows, against real FROST.
 *
 * Adding a chapter used to write a row and submit it, along with a submission
 * per paragraph; the rows said the submitter wrote them, because they had. Now
 * the group signs the chapter, and the claim this test exists to hold is that
 * the chapter every device ends up with is the group's: authored by the
 * threshold key, carrying a signature that verifies, with an id every member
 * arrives at independently -- and that the chunks each device splits out of it
 * are the same chunks.
 *
 * None of that is visible when it breaks. A row whose author is the proposer
 * looks exactly like a row whose author is the group -- both are opaque hex --
 * and a group that disagrees about the ids has two chapters that look like one.
 */
class SignedChapterTest {
    private val participants = 3
    private val threshold = 2

    /** The member who pasted the text. Nothing they own should end up on the rows. */
    private val proposer = "9".repeat(64)
    private val artifactVersionId = "b".repeat(64)

    private val originalText = "The first paragraph.\n\nThe second paragraph."

    /** Stands in for a completed ceremony; the test is about what gets signed, not the DKG. */
    private val keyMaterial: KeyMaterial = Frost.trustedDealerKeygen(
        thresholdSecretKey = PrivateKey(
            ByteVector32("1c0ffee0000000000000000000000000000000000000000000000000000000a1")
        ),
        nParticipants = participants,
        threshold = threshold
    )

    /**
     * The room the group signs in: derived from its key at the admin path, which
     * is what makes the room's id and the key it signs as one value.
     */
    private val room: SharedKeyDerivation.Derived = SharedKeyDerivation.derive(
        thresholdPublicKey = keyMaterial.thresholdPublicKey.value.toHex(),
        path = SharedKeyDerivation.MARMOT_ADMIN_GROUP_PATH
    )

    private val tweakCache: TweakCache = room.cache

    /** The group's nostr identity here, which is also [chatRoomId]. */
    private val groupPubKey = room.hex

    private val chatRoomId = room.hex

    private fun proposalTemplate(
        name: String = "Chapter 1",
        text: String = originalText,
        index: Int = 0,
    ) = ChapterEvent.build(
        artifactVersionId = artifactVersionId,
        name = name,
        originalText = text,
        index = index,
        wordCount = 6,
        characterCount = text.length,
        createdAt = 1_700_000_000L,
    )

    /**
     * Exactly what `FrostSigningManager.unsignedEventOf` does, and it must stay
     * exactly that: the proposer's fields re-authored under the group's key.
     */
    private fun unsignedEventOf(template: com.vitorpamplona.quartz.nip01Core.signers.EventTemplate<*>) = Event(
        id = EventHasher.hashId(
            pubKey = groupPubKey,
            createdAt = template.createdAt,
            kind = template.kind,
            tags = template.tags,
            content = template.content
        ),
        pubKey = groupPubKey,
        createdAt = template.createdAt,
        kind = template.kind,
        tags = template.tags,
        content = template.content,
        sig = ""
    )

    private fun sessionOver(unsignedEvent: Event) = FrostSigningSession(
        id = "s".repeat(64),
        chatRoomId = chatRoomId,
        coordinatorPublicKey = proposer,
        userPublicKey = proposer,
        dkgSessionId = "k".repeat(64),
        threshold = threshold,
        participantCount = participants,
        signerId = 0,
        derivationPath = SharedKeyDerivation.formatPath(),
        unsignedEventJson = unsignedEvent.toJson(),
        eventId = unsignedEvent.id,
        nonceRandom = "f".repeat(64)
    )

    /** A quorum signing the session's event, in the manager's order. */
    private fun groupSignature(session: FrostSigningSession): String {
        val message = ByteVector(session.eventId.hexToByteArray())
        val signerIds = listOf(0, 1)

        val nonces = signerIds.map { signerId ->
            SecretNonce.generate(
                sessionRandom = ByteVector32("a".repeat(63) + "${signerId + 1}"),
                secretShare = keyMaterial.secretShares[signerId],
                publicShare = keyMaterial.publicShares[signerId],
                tweakedThresholdPublicKey = tweakCache.tweakedPublicKey,
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
            tweakCache = tweakCache,
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

    /** Everything from the form to the rows a device holds afterwards. */
    private fun signedChapterEvent(
        name: String = "Chapter 1",
        text: String = originalText,
        index: Int = 0,
    ): ChapterEvent {
        val session = sessionOver(unsignedEventOf(proposalTemplate(name, text, index)))
        val signed = FrostSigningManager.signedEvent(session, groupSignature(session))

        return ChapterEvent(
            signed.id, signed.pubKey, signed.createdAt, signed.tags, signed.content, signed.sig
        )
    }

    @Test
    fun `the chapter the group signs is authored by the group, not the proposer`() {
        val chapter = MantraChapter.fromChapterEvent(signedChapterEvent(), chatRoomId)

        assertNotNull(chapter)
        assertEquals(groupPubKey, chapter.publicKey)
        assertNotEquals(proposer, chapter.publicKey)
    }

    @Test
    fun `the group it is authored by is the room it was signed in`() {
        // Signing runs at the room's derivation path, so the author is the room's
        // own id rather than the bare threshold key. That is what lets anybody
        // holding the row check it without being told which key to expect -- and
        // it is why the path a session signs at cannot come from the proposer.
        val chapter = MantraChapter.fromChapterEvent(signedChapterEvent(), chatRoomId)

        assertEquals(chatRoomId, chapter?.publicKey)
        assertNotEquals(
            keyMaterial.thresholdPublicKey.xOnly().value.toHex(),
            chapter?.publicKey,
            "a chapter must be signed by the room's key, not by the group's root key"
        )
    }

    @Test
    fun `the row's id is the id the group put its signature to`() {
        // Every device builds this row from the same signed event, so the id has
        // to be the one that was signed rather than anything recomputed from the
        // proposer. Otherwise members converge on nothing and each holds its own
        // copy of what is meant to be one chapter.
        val session = sessionOver(unsignedEventOf(proposalTemplate()))
        val signed = FrostSigningManager.signedEvent(session, groupSignature(session))

        val chapter = MantraChapter.fromChapterEvent(
            ChapterEvent(signed.id, signed.pubKey, signed.createdAt, signed.tags, signed.content, signed.sig),
            chatRoomId
        )

        assertEquals(session.eventId, chapter?.id)
    }

    @Test
    fun `the signature on the row verifies against the row's own id and author`() {
        // The payoff of signing rather than submitting: the row carries proof the
        // group made it, checkable by anybody holding it.
        val chapter = MantraChapter.fromChapterEvent(signedChapterEvent(), chatRoomId)

        assertNotNull(chapter)
        assertTrue(
            Nip01Crypto.verify(
                signature = chapter.signature.hexToByteArray(),
                hash = chapter.id.hexToByteArray(),
                pubKey = chapter.publicKey.hexToByteArray()
            ),
            "a chapter row should carry a signature the group's key made over its own id"
        )
    }

    @Test
    fun `what the form asked for is what the group signed`() {
        // The fields travel as tags through a session that knows nothing about
        // chapters. Anything dropped in there is signed away silently -- and the
        // original text most of all, since the chunks are split out of it.
        val chapter = MantraChapter.fromChapterEvent(
            signedChapterEvent(name = "In Detention", index = 2),
            chatRoomId
        )

        assertEquals("In Detention", chapter?.name)
        assertEquals(originalText, chapter?.originalText)
        assertEquals(artifactVersionId, chapter?.artifactVersionId)
        assertEquals(2, chapter?.index)
    }

    @Test
    fun `the chapter arrives with the chunks it splits into`() {
        // Nothing sends these rows: each device derives them from the chapter it
        // just applied. A translation is of a chunk rather than of a chapter, so
        // a chapter that arrives without them cannot be worked on.
        val signed = signedChapterEvent()

        val chapter = MantraChapter.fromChapterEvent(signed, chatRoomId)
        val chunks = MantraChunk.chunksOf(signed, chatRoomId)

        assertEquals(2, chunks.size)
        assertTrue(chunks.all { it.chapterId == chapter?.id })
        assertEquals(listOf("The first paragraph.", "The second paragraph."), chunks.map { it.text })
        assertTrue(chunks.all { it.publicKey == groupPubKey })
        // Derived, not signed: the group signed the chapter they were split from.
        assertTrue(chunks.all { it.signature == "" })
    }

    @Test
    fun `two members derive the same chunks from the chapter their group signed`() {
        // The reason the chunks can be derived at all. Both members hold the same
        // signed bytes, so both have to land on the same rows -- if they do not,
        // a translation one of them writes is of a chunk the other does not have.
        val signed = signedChapterEvent()

        val mine = MantraChunk.chunksOf(signed, chatRoomId)
        val theirs = MantraChunk.chunksOf(signed, chatRoomId)

        assertEquals(mine.map { it.id }, theirs.map { it.id })
    }
}
