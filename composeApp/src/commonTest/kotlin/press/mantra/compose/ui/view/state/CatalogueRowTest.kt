package press.mantra.compose.ui.view.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import press.mantra.compose.database.model.MantraDialect
import press.mantra.compose.language.AfricanLanguage

/**
 * Where each catalogue entry stands in a room, and what the search picks out.
 *
 * The standing is derived from `DialectScaffold.missingFrom` rather than
 * computed beside it, so the one case worth asserting hard is that a row never
 * says MISSING where that function disagrees: offering a member a dialect the
 * room already has would make a duplicate that cannot be merged, since dialect
 * ids differ by `createdAt`.
 */
class CatalogueRowTest {

    private val sesotho = AfricanLanguage("Sesotho", "st", "Lesotho")
    private val swahiliKenya = AfricanLanguage("Kiswahili", "sw", "Kenya")
    private val swahiliTanzania = AfricanLanguage("Kiswahili", "sw", "Tanzania")

    private val catalogue = listOf(sesotho, swahiliKenya, swahiliTanzania)

    private fun dialect(language: AfricanLanguage) = MantraDialect(
        id = "d${language.code}${language.country}".padEnd(64, 'f').take(64),
        publicKey = "p".repeat(64),
        name = language.name,
        country = language.country,
        language = language.code,
        signature = "",
        chatRoomId = "r".repeat(64),
    )

    @Test
    fun anEmptyRoomMakesEverythingMissing() {
        val rows = CatalogueRow.rowsOf(
            existing = emptyList(),
            pending = emptyList(),
            catalogue = catalogue,
        )

        assertEquals(catalogue, rows.map { it.language })
        assertTrue(rows.all { it.standing == CatalogueRow.Standing.MISSING })
    }

    @Test
    fun eachOfTheThreeStandingsIsReached() {
        val rows = CatalogueRow.rowsOf(
            existing = listOf(dialect(sesotho)),
            pending = listOf(swahiliKenya),
            catalogue = catalogue,
        )

        assertEquals(
            listOf(
                CatalogueRow.Standing.IN_THE_ROOM,
                CatalogueRow.Standing.IN_FLIGHT,
                CatalogueRow.Standing.MISSING,
            ),
            rows.map { it.standing }
        )
    }

    /**
     * A dialect that is both held and being signed again reads as held. That is
     * the more useful of the two -- it is theirs, whatever else is going on --
     * and either way it is not MISSING, which is the part that matters.
     */
    @Test
    fun heldBeatsInFlight() {
        val rows = CatalogueRow.rowsOf(
            existing = listOf(dialect(sesotho)),
            pending = listOf(sesotho),
            catalogue = catalogue,
        )

        assertEquals(CatalogueRow.Standing.IN_THE_ROOM, rows.first().standing)
    }

    @Test
    fun theOrderIsTheCataloguesAndEveryEntryIsThere() {
        val rows = CatalogueRow.rowsOf(
            existing = listOf(dialect(swahiliKenya)),
            pending = listOf(swahiliTanzania),
            catalogue = catalogue,
        )

        assertEquals(catalogue, rows.map { it.language })
    }

    @Test
    fun searchMatchesNameCountryAndCode() {
        assertTrue(sesotho.matches("seso"), "the name")
        assertTrue(sesotho.matches("LESOTHO"), "the country, whatever the case")
        assertTrue(sesotho.matches("  st "), "the code, trimmed")
        assertTrue(sesotho.matches(""), "an empty search excludes nothing")
        assertFalse(sesotho.matches("yoruba"))
    }
}
