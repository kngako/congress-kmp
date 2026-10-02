package press.mantra.compose.text

/**
 * What kind of leaf block a chunk came out of.
 *
 * Carried so [MarkdownProducer] can put the block's marker back when the
 * chunk's words have been replaced by a translation. Nothing else reads it.
 */
enum class BlockKind {
    Paragraph,
    Heading,
    SetextHeading,
    ListItem,
    Quote,

    /**
     * A whole table, as its own source.
     *
     * One of the three deliberate departures from markdown-it, which gives a
     * chunk per cell. See `docs/chapter-uploads.md`.
     */
    Table,
}

/**
 * One chunk, as the words and the marker that were in front of them.
 *
 * [text] is what a translator is given and what the group signs: the leaf
 * block's inline source with the **block** marker removed -- no `#`, no `> `,
 * no `- `, no setext underline -- while inline markup (`*italic*`, a link's
 * brackets) is kept verbatim. The first is structure a translation should not
 * be able to break by mistyping; the second is the translator's to carry.
 *
 * [markup] is the marker that was removed, kept so a document can be put back
 * together. For an ATX heading it is the run of `#`; for a list item the
 * bullet or the ordinal; for a blockquote `>`. It is not stored on the chunk
 * event -- it is recoverable from the chapter's own `original_text`, which is
 * where [MarkdownProducer] reads it from.
 */
data class MarkdownBlock(
    val kind: BlockKind,
    val markup: String,
    val text: String,
    /** The indent the block sat at, so a nested list item goes back where it was. */
    val indent: String = "",
)

/**
 * A run of lines, and the chunk it is -- or nothing, if it is not prose.
 *
 * A segmentation rather than a split, because two things need it. [block] being
 * null is a fenced code block, a thematic break, a link reference definition or
 * a blank line: material that carries no chunk and still has to be in the
 * document afterwards.
 *
 * [lines] is the exact source, so a document with no translations applied is
 * reassembled byte for byte by joining every segment's lines with `\n`.
 */
data class MarkdownSegment(
    val lines: List<String>,
    val block: MarkdownBlock?,
)

/**
 * The block structure of a chapter's markdown.
 *
 * A documented **subset** of CommonMark, and hand-rolled rather than taken from
 * a library on purpose: its output is hashed into chunk ids that a quorum
 * signs, so a dependency bump that moved one boundary would silently re-split
 * every chapter proposed afterwards and nothing in the app would notice. What
 * pins it instead is `MarkdownBlockFixtures`, generated from markdown-it by
 * `docs/scripts/markdown-block-fixtures.js`.
 *
 * What it recognises: ATX and setext headings, paragraphs with lazy
 * continuation, blockquotes, bullet and ordered list items including one level
 * of nesting, fenced and indented code, thematic breaks, tables, and link
 * reference definitions.
 *
 * What it does not, each of which has a fixture saying what happens instead:
 * HTML blocks (markdown-it's default is `html: false`, so a `<div>` is a
 * paragraph on both sides), footnotes, and blockquotes containing lists.
 */
internal object MarkdownBlocks {

    private val ATX = Regex("^ {0,3}(#{1,6})(?:[ \t]+(.*?))?[ \t]*#*[ \t]*$")
    private val SETEXT_UNDERLINE = Regex("^ {0,3}(=+|-+)[ \t]*$")
    private val THEMATIC_BREAK = Regex("^ {0,3}((\\*[ \t]*){3,}|(-[ \t]*){3,}|(_[ \t]*){3,})$")
    private val FENCE = Regex("^ {0,3}(`{3,}|~{3,})(.*)$")
    private val BULLET = Regex("^( {0,3})([-*+])([ \t]+)(.*)$")
    private val ORDERED = Regex("^( {0,3})(\\d{1,9}[.)])([ \t]+)(.*)$")
    private val QUOTE = Regex("^ {0,3}>[ \t]?(.*)$")
    private val REFERENCE_DEFINITION = Regex("^ {0,3}\\[[^\\]]+\\]:[ \t]*\\S.*$")
    private val INDENTED_CODE = Regex("^ {4,}\\S.*$")
    private val TABLE_DELIMITER = Regex("^ {0,3}\\|?[ \t]*:?-{1,}:?[ \t]*(\\|[ \t]*:?-{1,}:?[ \t]*)*\\|?[ \t]*$")

    fun segment(text: String): List<MarkdownSegment> {
        val lines = text.split("\n")
        val segments = mutableListOf<MarkdownSegment>()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]

            when {
                line.isBlank() -> {
                    segments += MarkdownSegment(listOf(line), null)
                    i++
                }

                FENCE.matches(line) -> {
                    val fence = FENCE.find(line)!!.groupValues[1]
                    val taken = mutableListOf(lines[i])
                    i++
                    while (i < lines.size) {
                        taken += lines[i]
                        val closing = FENCE.find(lines[i])?.groupValues?.get(1)
                        i++
                        if (closing != null && closing.length >= fence.length) break
                    }
                    // Departure 1: code is not prose, so no chunk.
                    segments += MarkdownSegment(taken, null)
                }

                THEMATIC_BREAK.matches(line) -> {
                    segments += MarkdownSegment(listOf(line), null)
                    i++
                }

                REFERENCE_DEFINITION.matches(line) -> {
                    // Departure 3, which is markdown-it's own behaviour with the
                    // `reference` rule enabled: a definition is not prose.
                    segments += MarkdownSegment(listOf(line), null)
                    i++
                }

                INDENTED_CODE.matches(line) -> {
                    val taken = mutableListOf<String>()
                    while (i < lines.size && (INDENTED_CODE.matches(lines[i]) || lines[i].isBlank())) {
                        // A blank line only belongs to the code block if more code
                        // follows it; otherwise it is the separator after it.
                        if (lines[i].isBlank() &&
                            lines.drop(i + 1).firstOrNull { it.isNotBlank() }
                                ?.let { !INDENTED_CODE.matches(it) } != false
                        ) {
                            break
                        }
                        taken += lines[i]
                        i++
                    }
                    segments += MarkdownSegment(taken, null)
                }

                ATX.matches(line) -> {
                    val match = ATX.find(line)!!
                    segments += MarkdownSegment(
                        lines = listOf(line),
                        block = MarkdownBlock(
                            kind = BlockKind.Heading,
                            markup = match.groupValues[1],
                            text = match.groupValues[2].trim(),
                        ),
                    )
                    i++
                }

                isTableStart(lines, i) -> {
                    val taken = mutableListOf<String>()
                    while (i < lines.size && lines[i].isNotBlank() && lines[i].contains('|')) {
                        taken += lines[i]
                        i++
                    }
                    // Departure 2: one chunk of the whole table's source.
                    segments += MarkdownSegment(
                        lines = taken,
                        block = MarkdownBlock(
                            kind = BlockKind.Table,
                            markup = "",
                            text = taken.joinToString("\n"),
                        ),
                    )
                }

                QUOTE.matches(line) -> {
                    val taken = mutableListOf<String>()
                    val inner = mutableListOf<String>()
                    while (i < lines.size && QUOTE.matches(lines[i])) {
                        taken += lines[i]
                        inner += QUOTE.find(lines[i])!!.groupValues[1]
                        i++
                    }
                    // A `>` on its own splits one blockquote into two paragraphs,
                    // which markdown-it reads as two inline runs and so two chunks.
                    val paragraphs = inner.splitOnBlank()
                    if (paragraphs.size <= 1) {
                        segments += MarkdownSegment(
                            lines = taken,
                            block = MarkdownBlock(
                                kind = BlockKind.Quote,
                                markup = ">",
                                text = inner.joinToString("\n").trim(),
                            ),
                        )
                    } else {
                        // Re-segmented per inner paragraph, so each is its own
                        // chunk and the source still reassembles exactly.
                        var consumed = 0
                        paragraphs.forEachIndexed { index, paragraph ->
                            val span = paragraph.size + if (index < paragraphs.size - 1) 1 else 0
                            segments += MarkdownSegment(
                                lines = taken.subList(consumed, consumed + span).toList(),
                                block = MarkdownBlock(
                                    kind = BlockKind.Quote,
                                    markup = ">",
                                    text = paragraph.joinToString("\n").trim(),
                                ),
                            )
                            consumed += span
                        }
                    }
                }

                BULLET.matches(line) || ORDERED.matches(line) -> {
                    val match = (BULLET.find(line) ?: ORDERED.find(line))!!
                    val indent = match.groupValues[1]
                    val marker = match.groupValues[2]
                    val gap = match.groupValues[3]
                    val taken = mutableListOf(line)
                    val content = mutableListOf(match.groupValues[4])
                    i++
                    // Lazy continuation: a plain line under an item belongs to it.
                    // A new marker, at any indent, is a new item -- which is what
                    // makes a nested list two chunks rather than one.
                    while (i < lines.size && isLazyContinuation(lines[i])) {
                        taken += lines[i]
                        content += lines[i].trim()
                        i++
                    }
                    segments += MarkdownSegment(
                        lines = taken,
                        block = MarkdownBlock(
                            kind = BlockKind.ListItem,
                            markup = marker + gap,
                            text = content.joinToString("\n").trim(),
                            indent = indent,
                        ),
                    )
                }

                else -> {
                    // A paragraph, unless the line after it underlines it.
                    val taken = mutableListOf(line)
                    i++
                    var setext: String? = null
                    while (i < lines.size) {
                        val next = lines[i]
                        if (SETEXT_UNDERLINE.matches(next) && !THEMATIC_BREAK.matches(next)) {
                            setext = SETEXT_UNDERLINE.find(next)!!.groupValues[1]
                            taken += next
                            i++
                            break
                        }
                        if (!isLazyContinuation(next)) break
                        taken += next
                        i++
                    }

                    segments += if (setext != null) {
                        MarkdownSegment(
                            lines = taken,
                            block = MarkdownBlock(
                                kind = BlockKind.SetextHeading,
                                markup = setext,
                                text = taken.dropLast(1).joinToString("\n").trim(),
                            ),
                        )
                    } else {
                        MarkdownSegment(
                            lines = taken,
                            block = MarkdownBlock(
                                kind = BlockKind.Paragraph,
                                markup = "",
                                text = taken.joinToString("\n").trim(),
                            ),
                        )
                    }
                }
            }
        }

        return segments
    }

    /**
     * Whether [line] continues the block above it rather than starting one.
     *
     * The list is every block start this scanner recognises. A line that starts
     * none of them, and is not blank, is prose belonging to whatever came
     * before -- which is what markdown-it calls a lazy continuation and is why
     * `First line.\nSecond line.` is one chunk and not two.
     */
    private fun isLazyContinuation(line: String): Boolean = when {
        line.isBlank() -> false
        ATX.matches(line) -> false
        THEMATIC_BREAK.matches(line) -> false
        FENCE.matches(line) -> false
        QUOTE.matches(line) -> false
        BULLET.matches(line) -> false
        ORDERED.matches(line) -> false
        REFERENCE_DEFINITION.matches(line) -> false
        else -> true
    }

    /** A table is a row of cells with a delimiter row under it. */
    private fun isTableStart(lines: List<String>, at: Int): Boolean =
        lines[at].contains('|') &&
            at + 1 < lines.size &&
            lines[at + 1].contains('-') &&
            TABLE_DELIMITER.matches(lines[at + 1])

    /** Splits a blockquote's inner lines into its paragraphs. */
    private fun List<String>.splitOnBlank(): List<List<String>> {
        val out = mutableListOf<List<String>>()
        var current = mutableListOf<String>()
        forEach { line ->
            if (line.isBlank()) {
                if (current.isNotEmpty()) {
                    out += current
                    current = mutableListOf()
                }
            } else {
                current += line
            }
        }
        if (current.isNotEmpty()) out += current
        return out
    }
}
