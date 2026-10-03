package press.mantra.compose.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The four rules [ChapterText.decode] exists for, and the one property that
 * makes them worth having: the same prose from two machines has to come out as
 * the same bytes, because those bytes become a chunk id a quorum signs.
 */
class ChapterTextTest {

    private fun decoded(bytes: ByteArray): String {
        val result = ChapterText.decode(bytes)
        assertTrue(result is ChapterText.Result.Text, "expected text, got $result")
        return result.markdown
    }

    @Test
    fun `plain utf8 markdown decodes unchanged`() {
        val markdown = "# Chapter one\n\nThe first paragraph.\n"
        assertEquals(markdown, decoded(markdown.encodeToByteArray()))
    }

    @Test
    fun `multi byte characters survive`() {
        // Setswana and a few of the diacritics a translation will actually carry.
        val markdown = "Ke a leboga.\n\nŠŌÜ — ôô.\n"
        assertEquals(markdown, decoded(markdown.encodeToByteArray()))
    }

    @Test
    fun `an invalid utf8 sequence is refused rather than replaced`() {
        // 0xFF is not a legal lead byte anywhere in UTF-8.
        val bytes = byteArrayOf(0x41, 0xFF.toByte(), 0x42)

        assertEquals(ChapterText.Result.NotText, ChapterText.decode(bytes))
    }

    @Test
    fun `a replacement character is never introduced`() {
        val bytes = byteArrayOf(0x41, 0xFF.toByte(), 0x42)
        val lenient = bytes.decodeToString()

        // The point of the rule: a lenient decode succeeds and hides the damage
        // inside text that would then be signed.
        assertTrue(lenient.contains('�'))
        assertEquals(ChapterText.Result.NotText, ChapterText.decode(bytes))
    }

    @Test
    fun `a byte order mark is stripped`() {
        val withBom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            "First paragraph.".encodeToByteArray()

        assertEquals("First paragraph.", decoded(withBom))
    }

    @Test
    fun `the same chapter from windows and from linux decodes identically`() {
        val crlf = "# Chapter one\r\n\r\nThe first paragraph.\r\nIts second line.\r\n"
        val lf = "# Chapter one\n\nThe first paragraph.\nIts second line.\n"

        assertEquals(decoded(lf.encodeToByteArray()), decoded(crlf.encodeToByteArray()))
        // And the thing the rule is actually protecting: without it the two are
        // different bytes over identical prose, so different chunk ids.
        assertNotEquals(crlf, lf)
    }

    @Test
    fun `a lone carriage return is normalised too`() {
        assertEquals("one\ntwo", decoded("one\rtwo".encodeToByteArray()))
    }

    @Test
    fun `splitting is unaffected by which newlines arrived`() {
        val crlf = decoded("One.\r\n\r\nTwo.\r\n\r\nThree.".encodeToByteArray())

        assertEquals(listOf("One.", "Two.", "Three."), Markdown.splitChunks(crlf))
    }

    @Test
    fun `a nul byte means the file is not text`() {
        val zipHeader = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x00) +
            "docx-ish".encodeToByteArray()

        assertEquals(ChapterText.Result.NotText, ChapterText.decode(zipHeader))
    }

    @Test
    fun `a nul past the sniff window is not looked for`() {
        // Documents the limit rather than claiming more than it does: the sniff
        // reads 8 KiB, and a file whose first 8 KiB are prose reads as prose.
        val bytes = "a".repeat(9 * 1024).encodeToByteArray() + byteArrayOf(0x00)

        assertTrue(ChapterText.decode(bytes) is ChapterText.Result.Text)
    }

    @Test
    fun `utf16 is refused because it is not supported rather than because it is binary`() {
        // FF FE is a UTF-16LE byte order mark and an illegal UTF-8 lead byte, so
        // this lands on NotText either way. Asserted so that a future lenient
        // decode cannot quietly turn it into a chapter of interleaved NULs.
        val utf16 = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x41, 0x00, 0x42, 0x00)

        assertEquals(ChapterText.Result.NotText, ChapterText.decode(utf16))
    }

    @Test
    fun `a file at the cap decodes and one byte over does not`() {
        val atCap = ByteArray(ChapterText.MAX_FILE_BYTES) { 'a'.code.toByte() }
        val overCap = ByteArray(ChapterText.MAX_FILE_BYTES + 1) { 'a'.code.toByte() }

        assertTrue(ChapterText.decode(atCap) is ChapterText.Result.Text)
        assertEquals(ChapterText.Result.TooLarge, ChapterText.decode(overCap))
    }

    @Test
    fun `an empty file is text not a failure`() {
        // Whether an empty chapter may be proposed is the form's decision; decode
        // only says what the bytes were.
        assertEquals("", decoded(ByteArray(0)))
    }
}
