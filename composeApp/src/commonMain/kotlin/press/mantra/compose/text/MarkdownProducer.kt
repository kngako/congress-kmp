package press.mantra.compose.text

/**
 * A chapter's markdown with translations in place of its words.
 *
 * The other half of [Markdown.splitBlocks], and the piece that makes stripping
 * block markers safe. A chunk carries `"Chapter one"` rather than
 * `"# Chapter one"` so that a translator cannot break the document by mistyping
 * the `#` -- which is only defensible if something can put the `#` back. Before
 * this, nothing in the app could: there was no equivalent of mantra.press's
 * `lib/markdown-producer/index.js` at all.
 *
 * **Substitution is by index, not by text**, and that is strictly better than
 * the thing it mirrors. mantra.press matches with
 * `translationChunks.find(tc => tc.text == token.content)`, so a chapter with
 * two identical paragraphs puts the first one's translation into both of them.
 * A chunk here already carries an `IndexTag`, so position is available and
 * exact.
 */
object MarkdownProducer {

    /**
     * [originalText] rebuilt with [translations] substituted by chunk index.
     *
     * A chunk with no translation keeps its own source **verbatim** -- not
     * re-rendered from its parsed form -- so a chapter with no translations at
     * all comes back byte for byte. That is the property worth having: it means
     * every markup-reassembly bug is one failing assertion away rather than a
     * difference somebody has to notice in a rendered document.
     *
     * A translated chunk is rendered from its [MarkdownBlock.kind]: the marker
     * goes back in front, and for a blockquote in front of every line, so a
     * translation that grew a line is still quoted.
     */
    fun produce(originalText: String, translations: Map<Int, String>): String {
        var chunkIndex = 0

        return MarkdownBlocks.segment(originalText).flatMap { segment ->
            val block = segment.block ?: return@flatMap segment.lines

            val translation = translations[chunkIndex]
            chunkIndex++

            if (translation.isNullOrEmpty()) segment.lines else render(block, translation)
        }.joinToString("\n")
    }

    /** How many chunks [originalText] has, which is what a caller indexes against. */
    fun chunkCount(originalText: String): Int = Markdown.splitBlocks(originalText).size

    private fun render(block: MarkdownBlock, translation: String): List<String> =
        when (block.kind) {
            BlockKind.Paragraph -> translation.lines()

            BlockKind.Heading -> listOf("${block.markup} $translation")

            // The underline has to be as long as the text, or it stops being an
            // underline -- a one-character `=` under a translated title is a
            // setext heading in CommonMark but reads as a typo.
            BlockKind.SetextHeading -> {
                // The underline matches the line immediately above it, which is
                // the last line of the translation rather than its first: a `=`
                // shorter than the text it underlines is still a setext heading
                // in CommonMark but reads as a typo.
                val lines = translation.lines()
                lines + block.markup.first().toString()
                    .repeat(lines.last().length.coerceAtLeast(1))
            }

            BlockKind.ListItem -> {
                val lines = translation.lines()
                listOf("${block.indent}${block.markup}${lines.first()}") +
                    // A continuation line is indented under the marker, so the
                    // item stays one item rather than becoming a paragraph after
                    // it.
                    lines.drop(1).map { "${block.indent}${" ".repeat(block.markup.length)}$it" }
            }

            BlockKind.Quote -> translation.lines().map { "> $it" }

            // A table's chunk is its own source, so its translation is too: there
            // is no marker to put back, and re-deriving the pipes from a
            // translated table would be guessing at its columns.
            BlockKind.Table -> translation.lines()
        }
}
