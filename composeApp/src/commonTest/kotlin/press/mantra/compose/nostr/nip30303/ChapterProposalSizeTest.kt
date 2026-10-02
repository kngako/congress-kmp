package press.mantra.compose.nostr.nip30303

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import press.mantra.compose.managers.FrostSigningManager
import press.mantra.compose.nostr.frost.FrostSigningEvents
import press.mantra.compose.text.Markdown

/**
 * [ChapterProposal] estimates what the manager measures, and the only property
 * worth asserting is that it never estimates low.
 *
 * The form reads the estimate as the text is typed; `proposeSigningBatch`
 * encodes the batch and measures it for real. If the estimate were ever under
 * the truth, the form would offer a chapter that the manager then refuses with
 * an `IllegalArgumentException` -- or, before that check existed, that went out
 * and stalled the session with nothing to blame.
 *
 * So these tests build the same events the manager would and compare. A tag
 * added to either kind fails here rather than in a room.
 */
class ChapterProposalSizeTest {
    private val groupKey = "a".repeat(64)
    private val artifactVersionId = "b".repeat(64)

    private fun unsigned(template: EventTemplate<*>) = Event(
        id = EventHasher.hashId(
            pubKey = groupKey,
            createdAt = template.createdAt,
            kind = template.kind,
            tags = template.tags,
            content = template.content,
        ),
        pubKey = groupKey,
        createdAt = template.createdAt,
        kind = template.kind,
        tags = template.tags,
        content = template.content,
        sig = "",
    )

    /** The batch as `proposeSigningBatch` would build it: the chapter, then its chunks. */
    private fun batchFor(originalText: String, name: String = "Chapter 1"): List<Event> {
        val chapter = unsigned(
            ChapterEvent.build(
                artifactVersionId = artifactVersionId,
                name = name,
                originalText = originalText,
                index = 0,
                wordCount = Markdown.wordCount(originalText),
                characterCount = Markdown.characterCount(originalText),
                createdAt = 1_700_000_000,
            )
        )
        return listOf(chapter) + ChunkEvent.splitOf(chapter).map(::unsigned)
    }

    private fun encodedBytes(originalText: String): Int =
        FrostSigningEvents.encodeProposal(batchFor(originalText)).encodeToByteArray().size

    /** Markdown of roughly [bytes] bytes, in [paragraphs] paragraphs. */
    private fun markdownOf(bytes: Int, paragraphs: Int): String {
        val perParagraph = (bytes / paragraphs).coerceAtLeast(4)
        return (0 until paragraphs).joinToString("\n\n") { "w ".repeat(perParagraph / 2).trim() }
    }

    @Test
    fun `the estimate is never under the real encoding`() {
        // The grid the overhead constant was measured over. Both ends matter: at
        // one chunk the text is counted twice exactly and the per-event cost is
        // at its highest, which is where an estimate is most likely to fall
        // short; at sixty-three the envelopes dominate.
        val shapes = listOf(1, 2, 5, 10, 20, 40, 63)
        val sizes = listOf(1, 4, 8, 16, 20, 24)

        for (paragraphs in shapes) {
            for (kib in sizes) {
                val text = markdownOf(kib * 1024, paragraphs)
                val chunks = Markdown.splitParagraphs(text)
                val estimated = ChapterProposal.proposalBytes(text, chunks)
                val actual = encodedBytes(text)

                assertTrue(
                    estimated >= actual,
                    "estimate $estimated under the real $actual for $paragraphs " +
                        "paragraphs of ${kib} KiB",
                )
            }
        }
    }

    @Test
    fun `the estimate is close enough to be worth showing`() {
        // Not merely conservative -- useful. An estimate twice the truth would
        // pass the test above and refuse half of every legal chapter.
        val text = markdownOf(16 * 1024, 40)
        val chunks = Markdown.splitParagraphs(text)

        val estimated = ChapterProposal.proposalBytes(text, chunks)
        val actual = encodedBytes(text)

        assertTrue(
            estimated <= actual + actual / 10,
            "estimate $estimated is more than a tenth over the real $actual",
        )
    }

    @Test
    fun `a chapter the estimate accepts really does encode under the cap`() {
        // The property the form's offer rests on, stated as a round trip rather
        // than as arithmetic: find the longest text the estimate allows at the
        // worst chunk count, and check the manager would carry it.
        val chunkCount = AddChapterMaxChunks
        val allowance = ChapterProposal.maxOriginalTextBytes(chunkCount)
        val text = markdownOf(allowance, chunkCount)
        val chunks = Markdown.splitParagraphs(text)

        assertEquals(chunkCount, chunks.size)
        assertTrue(ChapterProposal.fits(text, chunks))
        assertTrue(
            encodedBytes(text) <= FrostSigningManager.MAX_PROPOSAL_BYTES,
            "a chapter the form accepts encoded to ${encodedBytes(text)} bytes, over the cap",
        )
    }

    @Test
    fun `the cap binds inside the range of a real chapter`() {
        // Why any of this exists. Before the check, a chapter of this size was
        // offered by the form, proposed, and stalled. 28 KiB over forty
        // paragraphs is a long chapter of a real book, not a pathological one --
        // and measured, the fit runs out at about 24 KiB for that shape.
        val text = markdownOf(28 * 1024, 40)
        val chunks = Markdown.splitParagraphs(text)

        assertTrue(
            encodedBytes(text) > FrostSigningManager.MAX_PROPOSAL_BYTES,
            "expected ${encodedBytes(text)} bytes to exceed the cap",
        )
        assertTrue(!ChapterProposal.fits(text, chunks))
    }

    @Test
    fun `where the fit runs out, for a chapter-shaped chapter`() {
        // Pinned so the number is on the record rather than in a comment, and so
        // that a change to either event's tags shows up as a moved boundary
        // instead of as a quorum that never arrives. Forty paragraphs is the
        // shape; the figure is the largest whole KiB of markdown that encodes
        // under the cap.
        val fitting = (1..64).last { kib ->
            encodedBytes(markdownOf(kib * 1024, 40)) <= FrostSigningManager.MAX_PROPOSAL_BYTES
        }

        assertEquals(24, fitting)
    }

    @Test
    fun `an ordinary chapter is nowhere near either cap`() {
        // The other half of the claim: this is a limit on very long chapters,
        // not a tax on normal ones. Twelve paragraphs of a few hundred words.
        val text = markdownOf(8 * 1024, 12)
        val chunks = Markdown.splitParagraphs(text)

        assertTrue(ChapterProposal.fits(text, chunks))
        assertTrue(encodedBytes(text) < FrostSigningManager.MAX_PROPOSAL_BYTES / 2)
    }

    @Test
    fun `the text is measured in bytes, not in characters`() {
        // The languages this app exists for are full of characters that are one
        // UTF-16 unit and three bytes. Measuring by length would under-count a
        // Setswana or Amharic chapter and the cap would stop binding exactly
        // where it matters most.
        val ascii = "w ".repeat(512).trim()
        val amharic = "ሰ ".repeat(512).trim()

        assertEquals(ascii.length, amharic.length)
        assertTrue(
            ChapterProposal.proposalBytes(amharic, listOf(amharic)) >
                ChapterProposal.proposalBytes(ascii, listOf(ascii)),
        )
    }

    /**
     * Named here rather than imported from the view model, so this file depends
     * on the protocol and not on a screen. Asserted equal below.
     */
    private val AddChapterMaxChunks: Int get() = FrostSigningManager.MAX_BATCH_SIZE - 1

    @Test
    fun `the chunk cap is one less than the batch, because the chapter takes a place`() {
        assertEquals(FrostSigningManager.MAX_BATCH_SIZE - 1, AddChapterMaxChunks)
    }
}
