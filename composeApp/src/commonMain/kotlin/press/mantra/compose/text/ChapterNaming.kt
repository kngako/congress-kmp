package press.mantra.compose.text

/**
 * What to call a chapter that arrived as a file.
 *
 * The web app this mirrors has two answers and uses a different one per route:
 * the upload names a chapter `file.name` and the paste form names it
 * `chunks[0].trim()`. Both are right some of the time. `chapter-01.md` is a
 * filename, not a title, and a chapter whose first paragraph is prose has a
 * better name in its own text; a chapter whose file is called
 * `03 - The Wreck of the Hesperus.md` does not.
 *
 * So: the filename first, because somebody chose it, and the text only when the
 * filename says nothing a reader would want -- and a heading in the text before
 * a paragraph of it, because a heading is a title and a paragraph is the
 * beginning of the work.
 *
 * Whatever this returns is a **default in an editable field**, never the final
 * answer. A quorum is asked to sign the name along with the chapter, so the
 * person uploading has to be able to fix it first.
 */
object ChapterNaming {

    /** Past this, a name is the beginning of a paragraph rather than a title. */
    private const val MAX_DERIVED_LENGTH = 80

    private val LEADING_ORDINAL = Regex("^[\\s\\-_.0-9]+")

    fun forFile(fileName: String, markdown: String): String =
        fromFileName(fileName) ?: fromMarkdown(markdown) ?: ""

    /**
     * The filename with its extension and its ordinal prefix removed.
     *
     * `03 - The Wreck.md` is `The Wreck`; `chapter-01.md` is `chapter`, which is
     * a poor title but an honest reflection of a poor filename and is the one
     * the person will edit. What is dropped entirely is a name that is *only*
     * an ordinal -- `01.md`, `1.txt` -- because "01" as a chapter title is worse
     * than falling through to the text.
     */
    private fun fromFileName(fileName: String): String? =
        fileName.substringBeforeLast('.', fileName)
            .replace('_', ' ')
            .replace(LEADING_ORDINAL, "")
            .trim()
            .takeIf { it.isNotEmpty() }

    /**
     * The first heading in the text, or failing that its first line.
     *
     * Reads the markdown rather than [Markdown]'s split, because this runs while
     * a file is being looked at rather than while a chapter is being proposed,
     * and a heading's `#` has to be stripped either way.
     */
    private fun fromMarkdown(markdown: String): String? {
        val lines = markdown.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }

        val heading = lines.firstOrNull { it.startsWith("#") }
            ?.trimStart('#')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        if (heading != null) return heading.take(MAX_DERIVED_LENGTH)

        return lines.firstOrNull()?.take(MAX_DERIVED_LENGTH)
    }
}
