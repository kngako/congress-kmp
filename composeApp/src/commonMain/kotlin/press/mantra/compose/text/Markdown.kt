package press.mantra.compose.text

/**
 * Lightweight helpers for the markdown original text of chapters/chunks.
 *
 * Counts are computed on the raw markdown text (no rendering/stripping):
 * words are whitespace-separated tokens and characters are the string length.
 */
object Markdown {
    private val WHITESPACE = Regex("\\s+")


    fun wordCount(text: String): Int {
        val trimmed = text.trim()
        return if (trimmed.isEmpty()) 0 else trimmed.split(WHITESPACE).count { it.isNotBlank() }
    }

    fun characterCount(text: String): Int = text.length

    /**
     * The blocks a chapter's markdown splits into, one chunk each.
     *
     * A chunk is a **leaf** block's inline text with its block marker removed:
     * the words a translator is given and the words the group signs. Containers
     * -- lists, blockquotes -- contribute nothing of their own; a thematic
     * break, a code block and a link reference definition contribute nothing at
     * all.
     *
     * This is markdown-it's block token stream, which is what mantra.press
     * translates against, with the three departures recorded in
     * `docs/chapter-uploads.md`. It replaced a blank-line split, and the two
     * disagreed on ten cases in eleven -- on the text as well as the
     * boundaries, because markdown-it strips the marker, so `# Chapter one` is
     * `"Chapter one"`. Inline markup is kept verbatim: emphasis and links are
     * the translator's to carry, where a `#` mistyped into a translation breaks
     * the document.
     *
     * Its output is hashed into chunk ids a quorum signs, so it is pinned by
     * `MarkdownBlockFixtures` -- generated from markdown-it itself -- rather
     * than by this comment. See [MarkdownBlocks] for the subset it recognises.
     */
    fun splitBlocks(text: String): List<MarkdownBlock> =
        MarkdownBlocks.segment(text).mapNotNull { it.block }

    /** Just the words of each chunk, which is all an event carries. */
    fun splitChunks(text: String): List<String> = splitBlocks(text).map { it.text }
}
