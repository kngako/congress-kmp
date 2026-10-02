package press.mantra.compose.language

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The catalogue, held to the shape the rest of the feature reads it with.
 *
 * Nothing here is about which languages are in it -- that is a judgment call and
 * a reviewer's to make. It is about the invariants `DialectScaffold` matches on,
 * every one of which fails silently rather than loudly if it is broken: a
 * duplicated pair proposes the same dialect twice in one wave and the two do not
 * even collide, because their ids differ by `createdAt`.
 */
class AfricanLanguagesTest {

    @Test
    fun everyCodeIsAnIsoCodeShape() {
        AfricanLanguages.ALL.forEach { language ->
            assertEquals(
                language.code.lowercase(),
                language.code,
                "${language.name}'s code is not lowercase, and matching lowercases both sides"
            )
            assertTrue(
                language.code.length == 2 || language.code.length == 3,
                "${language.name}'s code ${language.code} is neither ISO 639-1 nor 639-3"
            )
            assertTrue(
                language.code.all { it in 'a'..'z' },
                "${language.name}'s code ${language.code} is not letters"
            )
        }
    }

    @Test
    fun everyEntryIsNamedAndPlaced() {
        AfricanLanguages.ALL.forEach { language ->
            assertTrue(language.name.isNotBlank(), "an entry for ${language.code} has no name")
            assertTrue(
                language.country.isNotBlank(),
                "${language.name} is not in a country, and a dialect is a language in a place"
            )
            assertEquals(
                language.name.trim(),
                language.name,
                "${language.name} has whitespace around it, which will end up in a signed event"
            )
            assertEquals(
                language.country.trim(),
                language.country,
                "${language.name}'s country has whitespace around it"
            )
        }
    }

    /**
     * The identity rule. Two entries sharing a code and a country are one
     * dialect proposed twice, and `DialectScaffold.missingFrom` cannot tell them
     * apart because there is nothing to tell apart.
     */
    @Test
    fun codeAndCountryTogetherAreUnique() {
        val duplicates = AfricanLanguages.ALL
            .groupBy { it.code to it.country }
            .filterValues { it.size > 1 }

        assertEquals(
            emptyMap(),
            duplicates,
            "these (code, country) pairs appear more than once"
        )
    }

    /**
     * A language in two countries is two entries, which is the whole model. Here
     * as an assertion rather than a comment because the uniqueness test above
     * reads as though it forbids this, and it does not.
     */
    @Test
    fun aLanguageMayAppearInSeveralCountries() {
        val swahili = AfricanLanguages.ALL.filter { it.code == "sw" }

        assertTrue(swahili.size > 1, "Kiswahili is spoken in more than one country")
        assertEquals(
            swahili.size,
            swahili.map { it.country }.distinct().size,
            "Kiswahili's entries do not name distinct countries"
        )
    }

    /**
     * Grouped by country, as the list says it is.
     *
     * Contiguity rather than alphabetical order: what a reader needs is for
     * their country's languages to be together, and `african` is the list that
     * promises it -- the European entries are appended and would break it.
     */
    @Test
    fun theAfricanEntriesAreGroupedByCountry() {
        val countries = AfricanLanguages.african.map { it.country }
        val firstSeen = countries.distinct()

        assertEquals(
            firstSeen,
            countries.zipWithNext()
                .filter { (previous, next) -> previous != next }
                .map { (_, next) -> next }
                .let { listOf(countries.first()) + it },
            "a country's languages are split apart in the list"
        )
    }

    @Test
    fun allIsTheTwoListsAndNothingElse() {
        assertEquals(
            AfricanLanguages.african + AfricanLanguages.european,
            AfricanLanguages.ALL
        )
        assertTrue(
            AfricanLanguages.african.isNotEmpty(),
            "a catalogue of African languages with no African languages in it"
        )
    }
}
