package press.mantra.compose.managers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import press.mantra.compose.database.model.MantraDialect
import press.mantra.compose.language.AfricanLanguage
import press.mantra.compose.language.AfricanLanguages
import press.mantra.compose.nostr.nip30303.DialectEvent

/**
 * What a room is missing from the catalogue, as a pure function.
 *
 * Every assertion here is about the identity rule, because that rule is the only
 * thing standing between this feature and a room full of duplicate dialects.
 * Nothing in the schema makes a dialect unique, and two proposals of the same
 * one do not even collide -- their ids differ by `createdAt` -- so a match this
 * function misses is a duplicate nobody will ever merge.
 */
class DialectScaffoldTest {

    private val chatRoomId = "r".repeat(64)

    private fun dialect(
        name: String,
        code: String,
        country: String,
    ) = MantraDialect(
        id = "$name$code$country".padEnd(64, 'f').take(64),
        publicKey = "p".repeat(64),
        name = name,
        country = country,
        language = code,
        signature = "",
        chatRoomId = chatRoomId,
    )

    private val sesotho = AfricanLanguage("Sesotho", "st", "Lesotho")
    private val swahiliKenya = AfricanLanguage("Kiswahili", "sw", "Kenya")
    private val swahiliTanzania = AfricanLanguage("Kiswahili", "sw", "Tanzania")

    private val catalogue = listOf(sesotho, swahiliKenya, swahiliTanzania)

    @Test
    fun aRoomWithNoDialectsIsMissingTheWholeCatalogue() {
        assertEquals(
            AfricanLanguages.ALL,
            DialectScaffold.missingFrom(existing = emptyList())
        )
    }

    @Test
    fun aHeldDialectIsNotMissingAndTheRestStillAre() {
        assertEquals(
            listOf(swahiliKenya, swahiliTanzania),
            DialectScaffold.missingFrom(
                existing = listOf(dialect("Sesotho", "st", "Lesotho")),
                catalogue = catalogue,
            )
        )
    }

    /** Typed by hand, with the whitespace and the capitals a person leaves behind. */
    @Test
    fun aHeldDialectStillCountsThroughWhitespaceAndCase() {
        assertEquals(
            listOf(swahiliKenya, swahiliTanzania),
            DialectScaffold.missingFrom(
                existing = listOf(dialect("sesotho", "  ST  ", " lesotho ")),
                catalogue = catalogue,
            )
        )
    }

    /**
     * The assertion that pins the identity rule. A room that typed the name
     * differently still holds the dialect, and proposing it again would be the
     * duplicate this whole function exists to avoid.
     */
    @Test
    fun theNameIsNotPartOfTheIdentity() {
        assertEquals(
            listOf(swahiliKenya, swahiliTanzania),
            DialectScaffold.missingFrom(
                existing = listOf(dialect("Southern Sotho", "st", "Lesotho")),
                catalogue = catalogue,
            )
        )
    }

    /** A dialect is a language in a place, so one country does not answer for another. */
    @Test
    fun theSameLanguageInAnotherCountryIsStillMissing() {
        assertEquals(
            listOf(sesotho, swahiliTanzania),
            DialectScaffold.missingFrom(
                existing = listOf(dialect("Kiswahili", "sw", "Kenya")),
                catalogue = catalogue,
            )
        )
    }

    @Test
    fun aDialectTheCatalogueDoesNotListChangesNothing() {
        assertEquals(
            catalogue,
            DialectScaffold.missingFrom(
                existing = listOf(dialect("Nheengatu", "yrl", "Brazil")),
                catalogue = catalogue,
            )
        )
    }

    @Test
    fun whatIsPendingIsNotMissing() {
        assertEquals(
            listOf(swahiliTanzania),
            DialectScaffold.missingFrom(
                existing = listOf(dialect("Sesotho", "st", "Lesotho")),
                pending = listOf(swahiliKenya),
                catalogue = catalogue,
            )
        )
    }

    @Test
    fun aRoomHoldingEverythingIsMissingNothing() {
        assertEquals(
            emptyList(),
            DialectScaffold.missingFrom(
                existing = catalogue.map { dialect(it.name, it.code, it.country) },
                catalogue = catalogue,
            )
        )
    }

    @Test
    fun dialectsOfEmitsOneDialectEventPerEntryInOrder() {
        val templates = DialectScaffold.dialectsOf(catalogue, createdAt = 1_700_000_000L)

        assertEquals(catalogue.size, templates.size)
        assertTrue(templates.all { it.kind == DialectEvent.KIND })
        assertTrue(
            templates.all { it.createdAt == 1_700_000_000L },
            "a wave proposed as one act should not read as entries that share a minute"
        )
    }

    /**
     * The round trip the feature actually depends on: what the picker chose is
     * what the row will say once the group has signed it.
     */
    @Test
    fun eachTemplateRoundTripsBackToItsCatalogueEntry() {
        val templates = DialectScaffold.dialectsOf(catalogue, createdAt = 1_700_000_000L)

        val rows = templates.mapNotNull { template ->
            MantraDialect.fromDialectEventTemplate(
                dialectEventTemplate = template,
                chatRoomId = chatRoomId,
                userPublicKey = "k".repeat(64),
            )
        }

        assertEquals(catalogue.size, rows.size, "a template did not parse back into a row")
        assertEquals(
            catalogue,
            rows.map { AfricanLanguage(name = it.name, code = it.language, country = it.country) }
        )
    }

    /**
     * Two entries of one wave are two different events. Obvious, and worth an
     * assertion: they share a `createdAt` and an author by construction, so the
     * only thing separating their ids is their tags.
     */
    @Test
    fun entriesOfOneWaveGetDistinctIds() {
        val rows = DialectScaffold.dialectsOf(catalogue, createdAt = 1_700_000_000L)
            .mapNotNull { template ->
                MantraDialect.fromDialectEventTemplate(
                    dialectEventTemplate = template,
                    chatRoomId = chatRoomId,
                    userPublicKey = "k".repeat(64),
                )
            }

        assertEquals(rows.size, rows.map { it.id }.distinct().size)
    }
}
