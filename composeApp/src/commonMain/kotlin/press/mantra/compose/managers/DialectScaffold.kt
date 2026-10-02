package press.mantra.compose.managers

import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import press.mantra.compose.database.model.MantraDialect
import press.mantra.compose.language.AfricanLanguage
import press.mantra.compose.language.AfricanLanguages
import press.mantra.compose.nostr.nip30303.DialectEvent

/**
 * Which catalogue languages a room has no dialect for, and the events to propose
 * them with.
 *
 * Built as a deliberate parallel to [TranslationScaffold], for the same reason:
 * the question "what is this room missing" is asked from the picker, answered
 * again when a wave comes back, and will be asked a third time by whatever
 * reconciles the two. Three copies of it would be three chances to disagree
 * about what a room already holds.
 *
 * Pure, and takes everything it reads. There is no repository here because the
 * answer has to be computable from a list of rows and a list of open proposals,
 * which is what makes it testable without a database and what lets a composable
 * ask it.
 */
object DialectScaffold {

    /**
     * What makes two dialects the same one: the language code and the country.
     *
     * Not the name. Nothing in the schema makes a dialect unique -- its id is a
     * content hash that includes `createdAt`, there is no index, and the rows are
     * signed by a quorum so they cannot be rewritten -- so duplication is
     * prevented here or not at all, and of the three fields the name is the only
     * one that is a matter of spelling. A member typing "Sesotho" and a catalogue
     * saying "Sesotho" agree until one of them carries a diacritic.
     */
    data class Key(val code: String, val country: String)

    /**
     * [code] and [country] as a [Key], trimmed and lowercased.
     *
     * `lowercase()` without a locale, deliberately: the no-argument form is
     * locale-invariant in Kotlin common, so a device set to Turkish does not
     * decide that `TI` and `ti` are different languages.
     */
    fun keyOf(code: String, country: String): Key = Key(
        code = code.trim().lowercase(),
        country = country.trim().lowercase(),
    )

    fun keyOf(language: AfricanLanguage): Key = keyOf(language.code, language.country)

    /** The dialect's `language` column is its code -- see [DialectEvent]'s language tag. */
    fun keyOf(dialect: MantraDialect): Key = keyOf(dialect.language, dialect.country)

    /**
     * The catalogue entries this room has no dialect for and none in flight, in
     * catalogue order.
     *
     * [pending] is what an open signing session is already signing, which the
     * caller works out with [pendingIn]. Leaving it empty is not a safe default
     * dressed as one -- it is the right answer for a caller that only wants to
     * know what the *rows* say, and the wrong one for a picker, where two admins
     * both seeing the same forty missing is how a room ends up with eighty
     * dialects that nothing will ever merge.
     */
    fun missingFrom(
        existing: List<MantraDialect>,
        pending: List<AfricanLanguage> = emptyList(),
        catalogue: List<AfricanLanguage> = AfricanLanguages.ALL,
    ): List<AfricanLanguage> {
        val held = existing.mapTo(mutableSetOf(), ::keyOf) + pending.map(::keyOf)

        return catalogue.filterNot { keyOf(it) in held }
    }

    /**
     * One dialect event per entry, in the order given.
     *
     * [createdAt] is stamped on all of them rather than read per event, for the
     * reason [TranslationScaffold.chaptersOf] does it: a wave proposed as one
     * act should read as one act, not as fifty dialects that happen to share a
     * minute. It also makes the wave's event ids a function of its inputs, which
     * is what makes a batch assertable.
     *
     * `DialectEvent.build`'s `language` parameter is the ISO code, not the name
     * -- the event's language tag has always held the code, and the form's
     * placeholder says so.
     */
    fun dialectsOf(
        languages: List<AfricanLanguage>,
        createdAt: Long,
    ): List<EventTemplate<DialectEvent>> = languages.map { language ->
        DialectEvent.build(
            name = language.name,
            country = language.country,
            language = language.code,
            createdAt = createdAt,
        )
    }
}
