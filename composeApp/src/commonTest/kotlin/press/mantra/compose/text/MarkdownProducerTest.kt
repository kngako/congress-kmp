package press.mantra.compose.text

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Putting a chapter back together with translations in it.
 *
 * The first test is the one that matters and is the reason stripping block
 * markers is safe at all: a chapter with no translations comes back byte for
 * byte. Every markup-reassembly bug is then one failing assertion rather than a
 * difference somebody has to notice in a rendered document.
 */
class MarkdownProducerTest {

    @Test
    fun `every fixture round-trips byte for byte with nothing translated`() {
        val wrong = MARKDOWN_BLOCK_FIXTURES.mapNotNull { fixture ->
            val out = MarkdownProducer.produce(fixture.markdown, emptyMap())
            if (out == fixture.markdown) null else "${fixture.name}: ${out.encodeToByteArray().size} vs ${fixture.markdown.encodeToByteArray().size}\n  got ${out.replace("\n", "\\n")}\n  was ${fixture.markdown.replace("\n", "\\n")}"
        }

        assertEquals(emptyList(), wrong, "\n" + wrong.joinToString("\n"))
    }

    @Test
    fun `a translated paragraph replaces its own words`() {
        val source = "First paragraph.\n\nSecond paragraph.\n"

        assertEquals(
            "First paragraph.\n\nDie tweede paragraaf.\n",
            MarkdownProducer.produce(source, mapOf(1 to "Die tweede paragraaf.")),
        )
    }

    @Test
    fun `a heading keeps its hashes`() {
        val source = "# Chapter one\n\nBody.\n"

        assertEquals(
            "# Kapitel een\n\nBody.\n",
            MarkdownProducer.produce(source, mapOf(0 to "Kapitel een")),
        )
    }

    @Test
    fun `a heading glued to its paragraph stays glued`() {
        // The converter's shape. Under the old blank-line split these were one
        // chunk, so the heading could not be translated apart at all.
        val source = "# Chapter one\nFirst paragraph.\n"

        assertEquals(
            "# Kapitel een\nDie eerste paragraaf.\n",
            MarkdownProducer.produce(source, mapOf(0 to "Kapitel een", 1 to "Die eerste paragraaf.")),
        )
    }

    @Test
    fun `a list item keeps its marker and its indent`() {
        val source = "- outer\n  - inner\n"

        assertEquals(
            "- buitenste\n  - binneste\n",
            MarkdownProducer.produce(source, mapOf(0 to "buitenste", 1 to "binneste")),
        )
    }

    @Test
    fun `an ordered list keeps its numbers`() {
        val source = "1. first\n2. second\n"

        assertEquals(
            "1. eerste\n2. tweede\n",
            MarkdownProducer.produce(source, mapOf(0 to "eerste", 1 to "tweede")),
        )
    }

    @Test
    fun `a blockquote is quoted again, line by line`() {
        val source = "> line one\n> line two\n"

        // Two lines in, three out: the marker goes in front of every line, so a
        // translation that grew a line is still quoted.
        assertEquals(
            "> een\n> twee\n> drie\n",
            MarkdownProducer.produce(source, mapOf(0 to "een\ntwee\ndrie")),
        )
    }

    @Test
    fun `a setext heading is underlined to the length of its translation`() {
        val source = "Title\n=====\n\nBody.\n"

        assertEquals(
            "Titel hier\n==========\n\nBody.\n",
            MarkdownProducer.produce(source, mapOf(0 to "Titel hier")),
        )
    }

    @Test
    fun `code is passed through untranslated and uncounted`() {
        val source = "Text.\n\n```\ncode();\n```\n\nMore.\n"

        // The fence carries no chunk, so index 1 is the paragraph after it --
        // which is the whole point of code not being a chunk.
        assertEquals(
            "Teks.\n\n```\ncode();\n```\n\nMeer.\n",
            MarkdownProducer.produce(source, mapOf(0 to "Teks.", 1 to "Meer.")),
        )
    }

    @Test
    fun `a thematic break and a reference definition are passed through`() {
        val source = "One.\n\n---\n\nSee [it][ref].\n\n[ref]: https://example.com\n"

        assertEquals(
            "Een.\n\n---\n\nSien [dit][ref].\n\n[ref]: https://example.com\n",
            MarkdownProducer.produce(source, mapOf(0 to "Een.", 1 to "Sien [dit][ref].")),
        )
    }

    @Test
    fun `two identical paragraphs translated differently keep their own words`() {
        // The bug in the thing this mirrors. mantra.press matches with
        // `translationChunks.find(tc => tc.text == token.content)`, so the first
        // translation goes into both. Chunks here carry an index, so position is
        // available and exact.
        val source = "Same words.\n\nSame words.\n"

        assertEquals(
            "Eerste.\n\nTweede.\n",
            MarkdownProducer.produce(source, mapOf(0 to "Eerste.", 1 to "Tweede.")),
        )
    }

    @Test
    fun `an empty translation leaves the original alone`() {
        // A translation chunk exists before it has words in it, so this is the
        // ordinary case for most of a chapter's life, not an edge one.
        val source = "First.\n\nSecond.\n"

        assertEquals(source, MarkdownProducer.produce(source, mapOf(0 to "", 1 to "")))
    }

    @Test
    fun `the chunk count agrees with the splitter`() {
        MARKDOWN_BLOCK_FIXTURES.forEach { fixture ->
            assertEquals(
                fixture.chunks.size,
                MarkdownProducer.chunkCount(fixture.markdown),
                fixture.name,
            )
        }
    }
}
