package press.mantra.compose.ui.view.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import press.mantra.compose.files.PickedFile

/**
 * What a picked file is offered as, and what the screen refuses to offer.
 *
 * The point of the review step is that nothing here reaches a quorum without
 * somebody having looked at it, so these assert the two halves of that: the
 * defaults are good enough that looking is quick, and everything that would be
 * wrong in a signed chapter is said before it is signed.
 */
class UploadReviewTest {

    private fun file(name: String, text: String) =
        PickedFile(name = name, bytes = text.encodeToByteArray())

    private fun reviewed(
        vararg files: PickedFile,
        existing: Map<String, String> = emptyMap(),
    ): List<PickedChapter> =
        UploadReview.withProblems(UploadReview.rows(files.toList(), startOrdinal = 0), existing)

    private fun paragraphs(count: Int) =
        (1..count).joinToString("\n\n") { "Paragraph $it." }

    @Test
    fun `an ordinary markdown file is offered with no problem`() {
        val rows = reviewed(file("03 - The Wreck.md", "# The wreck\n\nIt began badly.\n"))

        assertEquals(1, rows.size)
        assertNull(rows.single().problem)
        assertEquals("The Wreck", rows.single().name)
        // The heading is its own chunk and carries no `#`: a chunk is a leaf
        // block's words with the block marker taken off, so a translator cannot
        // break the document by mistyping it. See Markdown.splitBlocks.
        assertEquals(listOf("The wreck", "It began badly."), rows.single().chunks)
    }

    @Test
    fun `the name falls through to the text when the filename is only an ordinal`() {
        val rows = reviewed(file("01.md", "# Chapter one\n\nIt began badly.\n"))

        assertEquals("Chapter one", rows.single().name)
    }

    @Test
    fun `counts are of the decoded text not of the file`() {
        // CRLF in, LF counted: the row shows what would be signed.
        val rows = reviewed(file("a.md", "One two.\r\n\r\nThree.\r\n"))

        assertEquals(listOf("One two.", "Three."), rows.single().chunks)
        assertEquals(3, rows.single().wordCount)
    }

    @Test
    fun `a file that is not text is refused and blocks only its own row`() {
        val rows = reviewed(
            file("good.md", "Fine.\n"),
            PickedFile("cover.png", byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x00, 0x0D)),
            file("also-good.md", "Also fine.\n"),
        )

        assertEquals(UploadProblem.NotText, rows[1].problem)
        assertNull(rows[0].problem)
        assertNull(rows[2].problem)
    }

    @Test
    fun `an empty file has nothing to translate`() {
        assertEquals(UploadProblem.Empty, reviewed(file("blank.md", "   \n\n  ")).single().problem)
    }

    @Test
    fun `a name cleared by hand blocks the row until it is typed`() {
        val rows = reviewed(file("The Wreck.md", "It began badly.\n"))
        val cleared = UploadReview.withProblems(
            rows.map { it.copy(name = "  ") },
            emptyMap(),
        )

        assertEquals(UploadProblem.Unnamed, cleared.single().problem)

        val named = UploadReview.withProblems(
            cleared.map { it.copy(name = "The Wreck") },
            emptyMap(),
        )
        assertNull(named.single().problem)
    }

    @Test
    fun `too many paragraphs for one session is said per file`() {
        val rows = reviewed(
            file("long.md", paragraphs(UploadReview.MAX_CHUNKS_PER_CHAPTER + 1)),
            file("fine.md", paragraphs(UploadReview.MAX_CHUNKS_PER_CHAPTER)),
        )

        assertEquals(
            UploadProblem.TooManyChunks(UploadReview.MAX_CHUNKS_PER_CHAPTER + 1),
            rows[0].problem,
        )
        assertNull(rows[1].problem)
    }

    @Test
    fun `a chapter within the chunk cap but over what a proposal carries is refused`() {
        // Forty paragraphs is well inside the chunk cap, and 28 KiB of them is
        // not inside what one group message carries -- the two caps are
        // different limits and this is the file that proves it.
        val long = (1..40).joinToString("\n\n") { "w ".repeat(360).trim() }

        val problem = reviewed(file("fat.md", long)).single().problem

        assertTrue(problem is UploadProblem.TooLargeToPropose, "got $problem")
    }

    @Test
    fun `the same file picked twice warns rather than blocks`() {
        val rows = reviewed(
            file("the-wreck.md", "It began badly.\n"),
            file("copy-of-the-wreck.md", "It began badly.\n"),
        )

        assertNull(rows[0].problem)
        assertEquals(UploadProblem.Duplicate("the-wreck"), rows[1].problem)
        // Not blocking, because a book can repeat a page -- but two chapters
        // with the same text are two event ids that nothing later can merge.
        assertEquals(false, rows[1].problem?.blocking)
    }

    @Test
    fun `a file already in the version is recognised by its text`() {
        val rows = reviewed(
            file("ch1.md", "It began badly.\n"),
            existing = mapOf("It began badly.\n" to "The Wreck"),
        )

        assertEquals(UploadProblem.Duplicate("The Wreck"), rows.single().problem)
    }

    @Test
    fun `removing one row clears the warning on the other`() {
        val rows = reviewed(
            file("a.md", "Same words.\n"),
            file("b.md", "Same words.\n"),
        )
        assertEquals(UploadProblem.Duplicate("a"), rows[1].problem)

        val remaining = UploadReview.withProblems(rows.drop(1), emptyMap())

        // The reason a whole-list pass exists rather than a per-row check.
        assertNull(remaining.single().problem)
    }

    @Test
    fun `a proposed row is left exactly as it is`() {
        val proposed = reviewed(file("a.md", "Same words.\n"))
            .map { it.copy(sessionId = "session", name = "  ") }

        // Blank name, duplicate text, and still untouched: its chapter is in
        // front of the group and nothing here is a comment on that.
        val after = UploadReview.withProblems(
            proposed,
            existingByText = mapOf("Same words.\n" to "The Wreck"),
        )

        assertEquals(proposed, after)
    }

    @Test
    fun `rows appended later keep distinct ids`() {
        val first = UploadReview.rows(listOf(file("01.md", "One.")), startOrdinal = 0)
        val second = UploadReview.rows(listOf(file("01.md", "Two.")), startOrdinal = first.size)

        // Two directories can each hold a `01.md`, and a list keyed by name
        // would drop the second or animate the two into each other.
        assertEquals(2, (first + second).map { it.id }.toSet().size)
    }
}
