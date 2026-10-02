package press.mantra.compose.text

/**
 * Turning the bytes of a picked file into the markdown a chapter is made of.
 *
 * Deliberately not `bytes.decodeToString()`, which is what the web app this
 * mirrors does (`file.data.toString()` in `server/router/versions/index.js`).
 * There, a `.docx` dropped into the form becomes a chapter of zip-header
 * garbage that the uploader can go back and delete. Here the next thing that
 * happens to this text is that a quorum is asked to sign it, so what cannot be
 * read as prose has to be refused at the door rather than corrected afterwards.
 *
 * All four rules below exist because of that one difference. None of them is
 * about being tidy; each is about something that would otherwise be permanent.
 */
object ChapterText {

    /**
     * The most bytes a picked file may be.
     *
     * A crash guard, not the real limit -- what a chapter may actually carry is
     * bounded by what one signing proposal can put on the wire, which is far
     * tighter. This is here so that picking a video by mistake fails
     * on a comparison rather than on a megabyte-counting read, and so the
     * platform pickers have a number to stop reading at.
     */
    const val MAX_FILE_BYTES: Int = 1 * 1024 * 1024

    /** How far into a file to look for the NUL that says it is not text. */
    private const val SNIFF_BYTES: Int = 8 * 1024

    sealed interface Result {
        /** Decoded, BOM-stripped, newline-normalised markdown. */
        data class Text(val markdown: String) : Result

        /** Not decodable as UTF-8, or carrying a NUL. */
        data object NotText : Result

        /** Over [MAX_FILE_BYTES]. */
        data object TooLarge : Result
    }

    fun decode(bytes: ByteArray): Result {
        if (bytes.size > MAX_FILE_BYTES) return Result.TooLarge

        // Sniffed before decoding, not after. A file of UTF-16 text decodes as
        // invalid UTF-8 and would be refused anyway, but a file of mostly-ASCII
        // binary -- a zip's local header, a PDF's preamble -- can decode
        // cleanly and only reads as prose if nobody looks for the NUL.
        if (hasNul(bytes)) return Result.NotText

        // Strict, so an invalid sequence is refused rather than replaced. A
        // lenient decode substitutes U+FFFD, which is invisible in a text
        // field, is signed into the chapter's `original_text` tag, and is then
        // part of the group's record of the work for good.
        val decoded = runCatching {
            bytes.decodeToString(throwOnInvalidSequence = true)
        }.getOrElse { return Result.NotText }

        return Result.Text(normalise(decoded))
    }

    /**
     * The form of a chapter's markdown that is safe to hash.
     *
     * Two rewrites, both of which change the text's bytes without changing a
     * word of it, which is exactly why they belong here rather than at a call
     * site:
     *
     * **The byte-order mark goes.** A leading `U+FEFF` is invisible in the form
     * and in the chapter, and it is part of the first chunk's text -- so the
     * same opening paragraph saved by two editors is two different chunk ids,
     * and a translation of one does not match the other.
     *
     * **CRLF becomes LF.** [Markdown]'s paragraph rule already tolerates `\r\n`,
     * so splitting is unaffected; what is affected is the `original_text` tag,
     * which would carry the carriage returns. The same chapter uploaded from
     * Windows and from Linux would be two different events over identical
     * prose. A lone `\r` -- classic Mac line endings, and what some converters
     * still emit -- is normalised too, for the same reason.
     *
     * Public because the duplicate check on an upload compares chapters by
     * their text, and it has to compare the form that will be signed.
     */
    fun normalise(text: String): String =
        text.removePrefix("﻿")
            .replace("\r\n", "\n")
            .replace("\r", "\n")

    private fun hasNul(bytes: ByteArray): Boolean {
        val end = minOf(bytes.size, SNIFF_BYTES)
        for (index in 0 until end) {
            if (bytes[index] == 0.toByte()) return true
        }
        return false
    }
}
