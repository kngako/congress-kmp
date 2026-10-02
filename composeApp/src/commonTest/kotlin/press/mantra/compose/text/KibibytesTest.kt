package press.mantra.compose.text

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The figure in "19.9 KiB", which two screens show next to each other.
 *
 * Whole kibibytes would print a 20,473-byte chapter and its 21,226-byte limit
 * as the same "19 KiB", which reads as a form refusing something it says is
 * within the limit. And truncating rather than rounding is the whole point: a
 * figure shown as under the limit has to be under it.
 */
class KibibytesTest {

    @Test
    fun `whole kibibytes`() {
        assertEquals("0.0", kibibytes(0))
        assertEquals("1.0", kibibytes(1024))
        assertEquals("64.0", kibibytes(64 * 1024))
    }

    @Test
    fun `one decimal place`() {
        assertEquals("1.5", kibibytes(1024 + 512))
        assertEquals("19.9", kibibytes(20473))
        assertEquals("20.7", kibibytes(21226))
    }

    @Test
    fun `two figures a form shows together do not collapse into one`() {
        // The reason this is not whole kibibytes. Both of these round to 19.
        assertEquals(true, kibibytes(20473) != kibibytes(21226))
    }

    @Test
    fun `it truncates rather than rounds`() {
        // 1023 bytes is 0.999 KiB. Rounding would print "1.0", which is a figure
        // over the value it describes -- and when the value is being compared to
        // a cap, that is the direction that lies.
        assertEquals("0.9", kibibytes(1023))
        assertEquals("63.9", kibibytes(64 * 1024 - 1))
    }

    @Test
    fun `under a tenth of a kibibyte reads as zero point zero`() {
        // Honest rather than pretty: a 50-byte chapter is not 0.1 KiB.
        assertEquals("0.0", kibibytes(50))
    }
}
