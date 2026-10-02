package press.mantra.compose.language

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import kotlin.test.Test
import kotlin.test.assertTrue
import press.mantra.compose.managers.DialectScaffold
import press.mantra.compose.managers.FrostSigningManager
import press.mantra.compose.nostr.frost.FrostSigningEvents

/**
 * The catalogue against the two limits a signing proposal has.
 *
 * The tripwire for somebody adding a language, who has no reason to be thinking
 * about FROST. Neither limit is this file's to choose and neither is checked
 * anywhere a reviewer would see it: `proposeSigningBatch` enforces both with a
 * `require`, so the first sign of the catalogue outgrowing a batch would be a
 * wave that split in two, or one that threw.
 *
 * Modelled on `ChapterProposalSizeTest`, which does the same job for a chapter's
 * markdown, and measured the way the manager measures it -- the real encoding
 * rather than an estimate of it.
 */
class DialectCatalogueSizeTest {

    private val groupKey = "a".repeat(64)

    private fun unsigned(template: EventTemplate<*>) = Event(
        id = EventHasher.hashId(
            pubKey = groupKey,
            createdAt = template.createdAt,
            kind = template.kind,
            tags = template.tags,
            content = template.content,
        ),
        pubKey = groupKey,
        createdAt = template.createdAt,
        kind = template.kind,
        tags = template.tags,
        content = template.content,
        sig = "",
    )

    /** The wave as `proposeSigningBatch` would encode it, in bytes on the wire. */
    private fun encodedBytes(languages: List<AfricanLanguage>): Int =
        FrostSigningEvents
            .encodeProposal(
                DialectScaffold
                    .dialectsOf(languages = languages, createdAt = 1_700_000_000L)
                    .map(::unsigned)
            )
            .encodeToByteArray()
            .size

    /**
     * The whole catalogue is one wave.
     *
     * **If this fails, do not trim the catalogue.** The view model already
     * chunks a selection by `MAX_BATCH_SIZE`, so a larger catalogue is two
     * sessions rather than a bug -- what is lost is the property that a group
     * adopting the whole list answers once. Decide whether that is worth keeping,
     * and if it is not, turn this assertion into a note.
     */
    @Test
    fun theWholeCatalogueFitsOneBatch() {
        assertTrue(
            AfricanLanguages.ALL.size <= FrostSigningManager.MAX_BATCH_SIZE,
            "the catalogue holds ${AfricanLanguages.ALL.size} entries and a session signs at " +
                "most ${FrostSigningManager.MAX_BATCH_SIZE}, so adopting all of it is no " +
                "longer one wave -- read this test's note before trimming anything"
        )
    }

    /**
     * And it fits the byte limit, which is the one a `require` would throw on
     * before anything reached a relay.
     */
    @Test
    fun theWholeCatalogueFitsOneProposal() {
        val bytes = encodedBytes(AfricanLanguages.ALL)

        assertTrue(
            bytes <= FrostSigningManager.MAX_PROPOSAL_BYTES,
            "the catalogue encodes to $bytes bytes and a proposal carries at most " +
                "${FrostSigningManager.MAX_PROPOSAL_BYTES}"
        )
    }

    /**
     * A dialect stays cheap.
     *
     * Per entry rather than as a fraction of the budget, because the fraction
     * moves when a language is added and the per-entry cost does not -- so this
     * stays an assertion about the *event* instead of quietly becoming one about
     * the catalogue's length.
     *
     * A dialect is an id, an author, four single-value tags and a two- or
     * three-letter code, and measured at 301 bytes for the catalogue as it
     * stands. The bound is twice that, which catches a dialect event growing
     * something large while leaving the ordinary drift of a longer country name
     * alone. It is also self-consistent with [aFullBatchOfLongEntriesStillFits]:
     * sixty-four entries at this bound is well inside
     * [FrostSigningManager.MAX_PROPOSAL_BYTES].
     */
    @Test
    fun aDialectStaysCheapOnTheWire() {
        val perEntry = encodedBytes(AfricanLanguages.ALL) / AfricanLanguages.ALL.size

        assertTrue(
            perEntry <= MAX_BYTES_PER_ENTRY,
            "a dialect now costs $perEntry bytes in a proposal, over the " +
                "$MAX_BYTES_PER_ENTRY this test allows -- it was 301 when the bound was set"
        )
    }

    /** A full batch of the longest entries the catalogue could hold still proposes. */
    @Test
    fun aFullBatchOfLongEntriesStillFits() {
        val longest = AfricanLanguages.ALL.maxBy { it.name.length + it.country.length }

        val bytes = encodedBytes(
            (0 until FrostSigningManager.MAX_BATCH_SIZE).map { index ->
                longest.copy(country = "${longest.country} $index")
            }
        )

        assertTrue(
            bytes <= FrostSigningManager.MAX_PROPOSAL_BYTES,
            "a full batch of the catalogue's longest entry encodes to $bytes bytes"
        )
    }

    private companion object {
        /** See [aDialectStaysCheapOnTheWire]: twice the measured cost of one. */
        const val MAX_BYTES_PER_ENTRY = 602
    }
}
