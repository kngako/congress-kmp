package press.mantra.compose.text

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [Markdown.splitBlocks] against markdown-it, which is the parser mantra.press
 * translates with.
 *
 * The fixtures are generated rather than written: `splitBlocks` is hand-rolled,
 * its output is hashed into chunk ids a quorum signs, and "it looks right" is
 * not a standard that survives a tag being added to an event. See
 * `docs/scripts/markdown-block-fixtures.js`, which also encodes the three
 * deliberate departures so that this file is a plain equality check.
 */
class MarkdownBlockTest {

    @Test
    fun `every fixture splits as markdown-it does`() {
        val wrong = MARKDOWN_BLOCK_FIXTURES.mapNotNull { fixture ->
            val actual = Markdown.splitChunks(fixture.markdown)
            if (actual == fixture.chunks) {
                null
            } else {
                "${fixture.name}:\n  expected ${fixture.chunks}\n  actual   $actual"
            }
        }

        assertEquals(emptyList(), wrong, "\n" + wrong.joinToString("\n"))
    }

    @Test
    fun `the corpus is not empty so a generator that wrote nothing fails`() {
        assertEquals(true, MARKDOWN_BLOCK_FIXTURES.size >= 25)
    }
}
