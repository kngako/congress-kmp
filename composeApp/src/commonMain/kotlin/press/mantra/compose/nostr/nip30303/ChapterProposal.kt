package press.mantra.compose.nostr.nip30303

import press.mantra.compose.managers.FrostSigningManager
import press.mantra.compose.text.Markdown

/**
 * What proposing a chapter will cost on the wire, close enough to say so while
 * it is being typed.
 *
 * `FrostSigningManager.MAX_PROPOSAL_BYTES` is the limit and the manager enforces
 * it exactly, by encoding the batch and measuring it. That answer arrives too
 * late to be useful: by then the chapter has been built, the chunks cut and the
 * session is one line from being opened, and the only thing left to do with the
 * number is throw. This is the same question answered cheaply enough to put in
 * front of somebody with a cursor still in the text field.
 *
 * **Why a chapter needs this at all.** A chapter travels with a chunk per
 * paragraph, and the chapter carries the whole markdown while the chunks carry
 * the same words again between them -- see `ChapterEvent.originalText` for why
 * that duplication is wanted. So a proposal is a little over twice the prose,
 * plus an envelope per event, and the limit arrives at about 20 KiB of markdown
 * at the maximum chunk count. That is inside the range of a real book chapter.
 *
 * **Why this is not a constant.** A flat `MAX_ORIGINAL_TEXT_BYTES` was the
 * obvious shape and is wrong by half. The envelopes are what varies: measured,
 * a 63-chunk chapter fits about 20 KiB of markdown and a 10-chunk chapter fits
 * about 30. A single number safe for the first refuses a third of the second's
 * text for no reason, and a single number sized for the second stalls sessions.
 * The count is known as the text is typed, so the limit may as well be.
 */
object ChapterProposal {

    /**
     * What one unsigned event costs beside its own text.
     *
     * **Measured, not derived.** Across chapters from 1 KiB to 24 KiB and from 1
     * to 63 chunks, the per-event cost of an encoded proposal runs between 357
     * and 388 bytes: a 64-character id, a 64-character pubkey, an empty `sig`
     * (a proposal carries unsigned events, which is why this is smaller than a
     * signed event's ~370-byte floor), the JSON structure, the alt tag, and -- on
     * a chunk -- the 64-character chapter id it points at.
     *
     * Rounded up to 448 rather than to the measured maximum, and that direction
     * is the deliberate one. Over-estimating costs an author a slightly shorter
     * chapter; under-estimating costs the group a session that stalls with
     * nothing to blame. `ChapterProposalSizeTest` asserts the estimate stays at
     * or above the real encoding, so a tag added to either kind fails a test
     * rather than a quorum.
     */
    const val EVENT_OVERHEAD_BYTES: Int = 448

    /** The limit, which is the manager's and not this file's to choose. */
    const val MAX_PROPOSAL_BYTES: Int = FrostSigningManager.MAX_PROPOSAL_BYTES

    /**
     * What a proposal for this chapter would weigh.
     *
     * Takes the chunks rather than re-splitting, because every caller has just
     * counted them for the other cap and because summing their text is exact
     * where doubling the chapter's would not be -- the blank lines between
     * paragraphs are in the chapter's text and in no chunk's.
     */
    fun proposalBytes(originalText: String, chunks: List<String>): Int =
        originalText.utf8Size() +
            chunks.sumOf { it.utf8Size() } +
            (chunks.size + 1) * EVENT_OVERHEAD_BYTES

    fun proposalBytes(originalText: String): Int =
        proposalBytes(originalText, Markdown.splitParagraphs(originalText))

    fun fits(originalText: String, chunks: List<String>): Boolean =
        proposalBytes(originalText, chunks) <= MAX_PROPOSAL_BYTES

    /**
     * The most markdown a chapter of this many chunks may carry.
     *
     * For the form, which has to say what the limit *is* and not only that it
     * has been passed. Inverts [proposalBytes]: the envelopes come off the top
     * and the prose is counted twice, so what is left is halved.
     */
    fun maxOriginalTextBytes(chunkCount: Int): Int =
        ((MAX_PROPOSAL_BYTES - (chunkCount + 1) * EVENT_OVERHEAD_BYTES) / 2)
            .coerceAtLeast(0)
}

/**
 * The bytes a string takes as UTF-8, which is what the wire counts.
 *
 * Not `length`, which counts UTF-16 code units: the languages this app exists
 * for are full of characters that are one unit and three bytes, so measuring a
 * Setswana or Amharic chapter by its `length` would under-count it by half and
 * the cap would stop binding exactly where it matters most.
 */
private fun String.utf8Size(): Int = encodeToByteArray().size
