package press.mantra.compose.language

/**
 * A language the catalogue offers, in one of the countries it is spoken in.
 *
 * The unit is the pair rather than the language. A dialect in this app is
 * `(name, country, code)` -- see `MantraDialect` -- so Kiswahili is three
 * entries, one for Kenya, one for Tanzania and one for the DR Congo, and that is
 * the point rather than a duplication: a group translating for Kenyan readers is
 * not translating for Tanzanian ones. The whole reason the kind is called a
 * dialect is that it is finer than a language.
 *
 * [code] and [country] together are the identity. [name] is deliberately not
 * part of it: a member typing "Sesotho" into the form and a catalogue saying
 * "Sesotho" agree today and disagree the moment one of them carries a diacritic,
 * and of the three fields the name is the only one that is a matter of spelling
 * rather than of fact. See `DialectScaffold`.
 */
data class AfricanLanguage(
    /**
     * What the dialect row will be called.
     *
     * The name a speaker would recognise, which means the endonym wherever that
     * is also the usual name in English -- Sesotho, isiZulu, Kiswahili -- and the
     * English name where it is not. These are judgment calls and a reviewer is
     * welcome to overrule any of them; nothing matches on this field.
     */
    val name: String,
    /**
     * ISO 639-1 where the language has one, ISO 639-3 otherwise.
     *
     * Not 639-3 throughout, which would be more uniform and worse here: it would
     * make this catalogue's `sot` disagree with the `st` that every room which
     * used the hand-typed form has already signed, and those rows cannot be
     * rewritten -- a quorum signed them. The form's placeholder has always said
     * two letters (`eg. st`).
     */
    val code: String,
    val country: String,
)

/**
 * The languages most spoken in Africa, for populating a group's dialects without
 * typing each one into a three-field form.
 *
 * Reference data, deliberately outside `ui/`. `docs/scripts/m3-audit.sh` scans
 * `ui/` and `strings.xml` for string literals and title case, and "Northern
 * Sotho" is title case for exactly the reason `m3-title-case.py` keeps a
 * `SAMPLE` set of proper nouns at all. Keeping the catalogue out of `ui/` is
 * cheaper and more honest than an `m3-string-exempt` marker on a file that is
 * not UI.
 *
 * Also not a composeResource: there is no `files/` directory under
 * `composeResources`, a bundled JSON would need a parser and a failure mode, and
 * a Kotlin list is compile-checked, greppable and testable.
 *
 * **[ALL] has to stay under `FrostSigningManager.MAX_BATCH_SIZE`** or a wave
 * stops being one signing session. `DialectCatalogueSizeTest` is the tripwire,
 * because somebody adding a language has no reason to be thinking about FROST.
 *
 * Not exhaustive and not trying to be. It is the cross-border lingua francas and
 * the high-population national languages; a group wanting a language this does
 * not list still has the form.
 */
object AfricanLanguages {

    /**
     * Ordered by country, then by how widely the language is spoken there, so a
     * reader scanning for theirs scans once. `AfricanLanguagesTest` holds it to
     * that, which is why the European languages are a list of their own: they
     * are appended, and appending them would break the grouping.
     */
    val african: List<AfricanLanguage> = listOf(
        AfricanLanguage("Kabyle", "kab", "Algeria"),

        AfricanLanguage("Umbundu", "umb", "Angola"),
        AfricanLanguage("Kimbundu", "kmb", "Angola"),

        AfricanLanguage("Setswana", "tn", "Botswana"),

        AfricanLanguage("Mooré", "mos", "Burkina Faso"),

        AfricanLanguage("Kirundi", "rn", "Burundi"),

        AfricanLanguage("Sango", "sg", "Central African Republic"),

        // `lua` is Luba-Kasai, which is what Tshiluba means. ISO 639-1's `lu` is
        // Luba-Katanga, a different language of the same country, and taking the
        // two-letter code because it was there would have named the wrong one.
        AfricanLanguage("Lingala", "ln", "Democratic Republic of the Congo"),
        AfricanLanguage("Kiswahili", "sw", "Democratic Republic of the Congo"),
        AfricanLanguage("Tshiluba", "lua", "Democratic Republic of the Congo"),
        AfricanLanguage("Kikongo", "kg", "Democratic Republic of the Congo"),

        AfricanLanguage("Arabic", "ar", "Egypt"),

        AfricanLanguage("Tigrinya", "ti", "Eritrea"),

        AfricanLanguage("siSwati", "ss", "Eswatini"),

        AfricanLanguage("Amharic", "am", "Ethiopia"),
        AfricanLanguage("Oromo", "om", "Ethiopia"),

        AfricanLanguage("Akan", "ak", "Ghana"),
        AfricanLanguage("Ewe", "ee", "Ghana"),
        AfricanLanguage("Ga", "gaa", "Ghana"),

        AfricanLanguage("Kiswahili", "sw", "Kenya"),
        AfricanLanguage("Gikuyu", "ki", "Kenya"),
        AfricanLanguage("Dholuo", "luo", "Kenya"),

        AfricanLanguage("Sesotho", "st", "Lesotho"),

        AfricanLanguage("Malagasy", "mg", "Madagascar"),

        AfricanLanguage("Chichewa", "ny", "Malawi"),

        AfricanLanguage("Bambara", "bm", "Mali"),

        AfricanLanguage("Arabic", "ar", "Morocco"),
        AfricanLanguage("Tamazight", "zgh", "Morocco"),

        AfricanLanguage("Emakhuwa", "vmw", "Mozambique"),

        AfricanLanguage("Hausa", "ha", "Niger"),

        AfricanLanguage("Hausa", "ha", "Nigeria"),
        AfricanLanguage("Yoruba", "yo", "Nigeria"),
        AfricanLanguage("Igbo", "ig", "Nigeria"),

        AfricanLanguage("Kinyarwanda", "rw", "Rwanda"),

        AfricanLanguage("Wolof", "wo", "Senegal"),
        AfricanLanguage("Fulfulde", "ff", "Senegal"),

        AfricanLanguage("Krio", "kri", "Sierra Leone"),

        AfricanLanguage("Somali", "so", "Somalia"),

        // `nr` is South Ndebele and `nd` is North Ndebele, and both are called
        // isiNdebele by their speakers. The country tells them apart, which is
        // what a dialect being a language in a place is for.
        AfricanLanguage("isiZulu", "zu", "South Africa"),
        AfricanLanguage("isiXhosa", "xh", "South Africa"),
        AfricanLanguage("Afrikaans", "af", "South Africa"),
        AfricanLanguage("Sepedi", "nso", "South Africa"),
        AfricanLanguage("Setswana", "tn", "South Africa"),
        AfricanLanguage("Xitsonga", "ts", "South Africa"),
        AfricanLanguage("siSwati", "ss", "South Africa"),
        AfricanLanguage("Tshivenda", "ve", "South Africa"),
        AfricanLanguage("isiNdebele", "nr", "South Africa"),

        AfricanLanguage("Arabic", "ar", "Sudan"),

        AfricanLanguage("Kiswahili", "sw", "Tanzania"),

        AfricanLanguage("Luganda", "lg", "Uganda"),

        AfricanLanguage("Bemba", "bem", "Zambia"),

        AfricanLanguage("Shona", "sn", "Zimbabwe"),
        AfricanLanguage("isiNdebele", "nd", "Zimbabwe"),
    )

    /**
     * Not African languages, and among the most spoken on the continent.
     *
     * Included because a catalogue that cannot offer a group the language it
     * actually publishes in is a catalogue that group will not use, and one
     * country each because these are the countries the most speakers are in
     * rather than everywhere the language is official.
     *
     * Leaving them out would make this a catalogue of African languages instead,
     * which is a defensible thing to want -- so they are a list of their own
     * rather than three lines buried in [african], and dropping them is one edit
     * to [ALL].
     */
    val european: List<AfricanLanguage> = listOf(
        AfricanLanguage("French", "fr", "Democratic Republic of the Congo"),
        AfricanLanguage("Portuguese", "pt", "Angola"),
        AfricanLanguage("English", "en", "Nigeria"),
    )

    /** Every entry the picker offers. */
    val ALL: List<AfricanLanguage> = african + european
}
