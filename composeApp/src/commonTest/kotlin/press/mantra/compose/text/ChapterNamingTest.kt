package press.mantra.compose.text

import kotlin.test.Test
import kotlin.test.assertEquals

class ChapterNamingTest {

    private val body = "The first paragraph.\n\nThe second.\n"

    @Test
    fun `a filename somebody chose is the name`() {
        assertEquals(
            "The Wreck of the Hesperus",
            ChapterNaming.forFile("The Wreck of the Hesperus.md", body),
        )
    }

    @Test
    fun `an ordinal prefix is dropped`() {
        assertEquals("The Wreck", ChapterNaming.forFile("03 - The Wreck.md", body))
        assertEquals("The Wreck", ChapterNaming.forFile("1. The Wreck.markdown", body))
    }

    @Test
    fun `underscores read as spaces`() {
        assertEquals("the wreck", ChapterNaming.forFile("the_wreck.txt", body))
    }

    @Test
    fun `a filename that is only an ordinal falls through to the text`() {
        assertEquals("The first paragraph.", ChapterNaming.forFile("01.md", body))
    }

    @Test
    fun `a heading in the text beats the first paragraph`() {
        assertEquals(
            "Chapter one",
            ChapterNaming.forFile("1.md", "# Chapter one\n\nThe first paragraph.\n"),
        )
    }

    @Test
    fun `a heading glued to its paragraph is still found`() {
        // The shape mantra.press's TeX converter emits: a heading and the
        // paragraph under it separated by a single newline.
        assertEquals(
            "Chapter one",
            ChapterNaming.forFile("1.md", "# Chapter one\nThe first paragraph.\n"),
        )
    }

    @Test
    fun `a derived name is cut to a title's length`() {
        val long = "w ".repeat(200)

        assertEquals(80, ChapterNaming.forFile("1.md", long).length)
    }

    @Test
    fun `nothing to go on gives an empty name for the form to ask about`() {
        assertEquals("", ChapterNaming.forFile("01.md", "   \n\n  \n"))
    }

    @Test
    fun `a file with no extension keeps its whole name`() {
        assertEquals("CHAPTER ONE", ChapterNaming.forFile("CHAPTER ONE", body))
    }
}
