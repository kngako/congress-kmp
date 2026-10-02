package press.mantra.compose.ui.view.state

import press.mantra.compose.database.model.MantraDialect
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.language.AfricanLanguage
import press.mantra.compose.language.AfricanLanguages
import press.mantra.compose.managers.DialectScaffold

sealed interface AddDialectsFromCatalogueUIState {
    data class Loaded(
        val localChatRoom: LocalChatRoom,

        /**
         * Every catalogue entry and where it stands in this room. Loaded is
         * reached before these are known, so an empty list here means "still
         * reading" rather than "nothing to show".
         */
        val rows: List<CatalogueRow> = emptyList(),

        /**
         * Whether the group holds a shared key. A dialect is signed into
         * existence rather than submitted, so a group without one cannot make
         * any here -- the same gate the hand-typed form has.
         */
        val canSign: Boolean = false,
    ): AddDialectsFromCatalogueUIState {
        val missing: List<AfricanLanguage>
            get() = rows.filter { it.standing == CatalogueRow.Standing.MISSING }
                .map { it.language }
    }

    data class Error(
        val message: String
    ): AddDialectsFromCatalogueUIState

    data object Loading: AddDialectsFromCatalogueUIState
}

/**
 * A catalogue entry, and whether this room can still ask for it.
 *
 * Three standings rather than two, because "not available" has two different
 * explanations and telling a member the wrong one is worse than telling them
 * nothing: a dialect the room holds is theirs, and a dialect in flight is a
 * proposal somebody else opened and they can go and sign.
 */
data class CatalogueRow(
    val language: AfricanLanguage,
    val standing: Standing,
) {
    enum class Standing {
        /** Nothing holds it and nothing is signing it. The only tickable state. */
        MISSING,

        /** A `MantraDialect` row for this (code, country) is already here. */
        IN_THE_ROOM,

        /** An open signing session is signing it. See `DialectScaffold.pendingIn`. */
        IN_FLIGHT,
    }

    companion object {
        /**
         * Every catalogue entry, standing against what this room holds and what
         * it is signing.
         *
         * Derived from [DialectScaffold.missingFrom] rather than computed beside
         * it, deliberately. That function is what the proposal is built from, so
         * a row saying MISSING where it disagreed would offer a member a dialect
         * the room already has -- and the duplicate would not even collide,
         * because dialect ids differ by `createdAt`. One authority; the other two
         * standings are only labels on what it already excluded.
         */
        fun rowsOf(
            existing: List<MantraDialect>,
            pending: List<AfricanLanguage>,
            catalogue: List<AfricanLanguage> = AfricanLanguages.ALL,
        ): List<CatalogueRow> {
            val missing = DialectScaffold
                .missingFrom(existing = existing, pending = pending, catalogue = catalogue)
                .mapTo(mutableSetOf(), DialectScaffold::keyOf)

            val held = existing.mapTo(mutableSetOf(), DialectScaffold::keyOf)

            return catalogue.map { language ->
                val key = DialectScaffold.keyOf(language)

                CatalogueRow(
                    language = language,
                    standing = when {
                        key in missing -> Standing.MISSING
                        key in held -> Standing.IN_THE_ROOM
                        else -> Standing.IN_FLIGHT
                    },
                )
            }
        }
    }
}

/**
 * Whether [query] picks this language out, matched on its name, its country and
 * its code.
 *
 * The code is matched too because it is what a member who knows ISO 639 will
 * type, and because it is the one field of the three that is not a matter of
 * spelling -- somebody looking for Sepedi may type `nso` rather than guess
 * whether this catalogue calls it Sepedi or Northern Sotho.
 */
fun AfricanLanguage.matches(query: String): Boolean {
    val needle = query.trim().lowercase()

    return needle.isEmpty() ||
        name.lowercase().contains(needle) ||
        country.lowercase().contains(needle) ||
        code.lowercase().contains(needle)
}
