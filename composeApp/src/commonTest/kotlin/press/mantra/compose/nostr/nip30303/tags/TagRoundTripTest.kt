package press.mantra.compose.nostr.nip30303.tags

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.tags.events.EventReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The twenty-four tags in this package were written from one template, and the
 * template is the hazard: every one of them declares `parse` beside a `TAG_NAME`
 * two lines above it, so a file copied for a new tag can keep the old file's
 * class in both the return type and the `return` while the name guard reads
 * correctly. `TranslationArtifactVersionIdTag` did exactly that -- it parsed the
 * right slot off the right tag name and handed back a `DialectIdTag`. Nothing
 * failed, because every caller in the app reaches straight through to `.ref` and
 * an event id is an event id. A caller that branched on the type, or collected
 * parse results across the family, would have got a dialect reference where a
 * translation reference belongs.
 *
 * So the type is the assertion here, not the value. Each tag is handed to
 * [roundTrip] through its own `::parse`, with its own class **written out** as
 * the type argument: a `parse` whose *declared* return type names a sibling then
 * no longer satisfies `(Array<String>) -> T?`, and the file stops compiling on
 * the line of the offending tag -- earlier and more precisely than any runtime
 * check. [assertIs] covers the other half, a declaration that is right over a
 * `return` that is not.
 *
 * The explicit `<Tag>` is the whole mechanism and must not be tidied away. Left
 * to inference `T` is read *off the function reference*, so a `parse` returning
 * `DialectIdTag` simply makes `T` be `DialectIdTag`, every member these classes
 * share resolves, and the test passes while the bug sits there. That is how the
 * first draft of this file missed it.
 *
 * A new tag in this package belongs in the list for its shape below. There are
 * four shapes: an [EventReference], a single string, counts, and the two that
 * carry something of their own.
 */
class TagRoundTripTest {
    private val eventId = "a".repeat(64)
    private val author = "b".repeat(64)
    private val relay = RelayUrlNormalizer.normalize("wss://relay.mantra.press")

    /**
     * Asserts that [parse] accepts what `assemble` produced and returns a [T].
     *
     * [name] is passed separately from the array so the tag's own `TAG_NAME` is
     * checked against slot 0 rather than read out of it.
     */
    private inline fun <reified T : Any> roundTrip(
        name: String,
        assembled: Array<String>,
        parse: (Array<String>) -> T?,
    ): T {
        assertEquals(name, assembled[0], "assemble wrote the wrong name into slot 0")
        val parsed = assertNotNull(
            parse(assembled),
            "$name: parse rejected its own assemble output",
        )
        return assertIs<T>(parsed, "$name: parse returned the wrong class")
    }

    private inline fun <reified T : Any> refRoundTrip(
        name: String,
        assembled: Array<String>,
        parse: (Array<String>) -> T?,
        ref: (T) -> EventReference,
    ) {
        val parsed = ref(roundTrip(name, assembled, parse))

        assertEquals(eventId, parsed.eventId, "$name: event id did not survive the round trip")
        assertEquals(author, parsed.author, "$name: author did not survive the round trip")
        assertEquals(relay, parsed.relayHint, "$name: relay hint did not survive the round trip")
    }

    // --- the eleven that carry an event reference ---

    @Test
    fun `every event-reference tag parses back as itself`() {
        refRoundTrip<ArtifactIdTag>(
            ArtifactIdTag.TAG_NAME,
            ArtifactIdTag.assemble(eventId, relay, author),
            ArtifactIdTag::parse,
        ) { it.ref }

        refRoundTrip<ArtifactVersionIdTag>(
            ArtifactVersionIdTag.TAG_NAME,
            ArtifactVersionIdTag.assemble(eventId, relay, author),
            ArtifactVersionIdTag::parse,
        ) { it.ref }

        refRoundTrip<ChapterIdTag>(
            ChapterIdTag.TAG_NAME,
            ChapterIdTag.assemble(eventId, relay, author),
            ChapterIdTag::parse,
        ) { it.ref }

        refRoundTrip<ChunkIdTag>(
            ChunkIdTag.TAG_NAME,
            ChunkIdTag.assemble(eventId, relay, author),
            ChunkIdTag::parse,
        ) { it.ref }

        refRoundTrip<DialectIdTag>(
            DialectIdTag.TAG_NAME,
            DialectIdTag.assemble(eventId, relay, author),
            DialectIdTag::parse,
        ) { it.ref }

        refRoundTrip<PayloadIdTag>(
            PayloadIdTag.TAG_NAME,
            PayloadIdTag.assemble(eventId, relay, author),
            PayloadIdTag::parse,
        ) { it.ref }

        refRoundTrip<ProjectIdTag>(
            ProjectIdTag.TAG_NAME,
            ProjectIdTag.assemble(eventId, relay, author),
            ProjectIdTag::parse,
        ) { it.ref }

        // The one the template got wrong. Before the fix this call would not
        // compile: `::parse` was declared `(Array<String>) -> DialectIdTag?`.
        refRoundTrip<TranslationArtifactVersionIdTag>(
            TranslationArtifactVersionIdTag.TAG_NAME,
            TranslationArtifactVersionIdTag.assemble(eventId, relay, author),
            TranslationArtifactVersionIdTag::parse,
        ) { it.ref }

        refRoundTrip<TranslationChapterIdTag>(
            TranslationChapterIdTag.TAG_NAME,
            TranslationChapterIdTag.assemble(eventId, relay, author),
            TranslationChapterIdTag::parse,
        ) { it.ref }

        refRoundTrip<TranslationChunkIdTag>(
            TranslationChunkIdTag.TAG_NAME,
            TranslationChunkIdTag.assemble(eventId, relay, author),
            TranslationChunkIdTag::parse,
        ) { it.ref }

        refRoundTrip<TranslationIdTag>(
            TranslationIdTag.TAG_NAME,
            TranslationIdTag.assemble(eventId, relay, author),
            TranslationIdTag::parse,
        ) { it.ref }
    }

    /**
     * An author with no relay hint still lands in slot 3.
     *
     * `arrayOfNotNull` drops only *trailing* nulls and writes `""` for an
     * interior one, so a reference assembled without a hint is
     * `[name, id, "", author]` rather than `[name, id, author]`. If that ever
     * changed the author would be read as a relay hint and silently dropped,
     * which is the shape most of these tags are assembled in.
     */
    @Test
    fun `an event-reference tag keeps its author when the relay hint is absent`() {
        val assembled = ArtifactIdTag.assemble(eventId, relay = null, pubkey = author)
        val parsed = assertNotNull(ArtifactIdTag.parse(assembled)).ref

        assertEquals(eventId, parsed.eventId)
        assertEquals(author, parsed.author)
        assertNull(parsed.relayHint)
    }

    // --- the eight that carry one string, and the version label that behaves like one ---

    @Test
    fun `every single-value tag parses back as itself`() {
        assertEquals(
            "Botswana",
            roundTrip<CountryTag>(
                CountryTag.TAG_NAME,
                CountryTag.assemble("Botswana"),
                CountryTag::parse,
            ).country,
        )

        assertEquals(
            author,
            roundTrip<CreatorTag>(
                CreatorTag.TAG_NAME,
                CreatorTag.assemble(author),
                CreatorTag::parse,
            ).pubKey,
        )

        assertEquals(
            "tn",
            roundTrip<LanguageTag>(
                LanguageTag.TAG_NAME,
                LanguageTag.assemble("tn"),
                LanguageTag::parse,
            ).language,
        )

        assertEquals(
            "CC-BY-SA-4.0",
            roundTrip<LicenseTag>(
                LicenseTag.TAG_NAME,
                LicenseTag.assemble("CC-BY-SA-4.0"),
                LicenseTag::parse,
            ).license,
        )

        assertEquals(
            "Gospel of John",
            roundTrip<NameTag>(
                NameTag.TAG_NAME,
                NameTag.assemble("Gospel of John"),
                NameTag::parse,
            ).name,
        )

        assertEquals(
            "In the beginning was the Word",
            roundTrip<OriginalTextTag>(
                OriginalTextTag.TAG_NAME,
                OriginalTextTag.assemble("In the beginning was the Word"),
                OriginalTextTag::parse,
            ).originalText,
        )

        assertEquals(
            "https://mantra.press/john",
            roundTrip<UrlTag>(
                UrlTag.TAG_NAME,
                UrlTag.assemble("https://mantra.press/john"),
                UrlTag::parse,
            ).url,
        )

        assertEquals(
            "private",
            roundTrip<VisibilityTag>(
                VisibilityTag.TAG_NAME,
                VisibilityTag.assemble("private"),
                VisibilityTag::parse,
            ).visibility,
        )

        assertEquals(
            "v2",
            roundTrip<ArtifactVersionMetadataTag>(
                ArtifactVersionMetadataTag.TAG_NAME,
                ArtifactVersionMetadataTag.assemble("v2"),
                ArtifactVersionMetadataTag::parse,
            ).versionLabel,
        )
    }

    // --- the three that carry numbers through a string slot ---

    @Test
    fun `every numeric tag parses back as itself`() {
        assertEquals(
            7,
            roundTrip<IndexTag>(
                IndexTag.TAG_NAME,
                IndexTag.assemble(7),
                IndexTag::parse,
            ).index,
        )

        assertEquals(
            30303,
            roundTrip<PayloadKindTag>(
                PayloadKindTag.TAG_NAME,
                PayloadKindTag.assemble(30303),
                PayloadKindTag::parse,
            ).kind,
        )

        val stats = roundTrip<WordStatisticsTag>(
            WordStatisticsTag.TAG_NAME,
            WordStatisticsTag.assemble(wordCount = 412, characterCount = 2317),
            WordStatisticsTag::parse,
        )
        assertEquals(412, stats.wordCount)
        assertEquals(2317, stats.characterCount)
    }

    // --- and the one with a role beside the key ---

    @Test
    fun `the contributor tag parses back as itself with its role`() {
        val parsed = roundTrip<ContributorTag>(
            ContributorTag.TAG_NAME,
            ContributorTag.assemble(author, ContributorTag.ROLE_TRANSLATOR),
            ContributorTag::parse,
        )

        assertEquals(author, parsed.pubKey)
        assertEquals(ContributorTag.ROLE_TRANSLATOR, parsed.role)
    }

    /**
     * No tag in the package answers to another tag's name.
     *
     * The wire names are not a tidy family -- six carry a stray `Tag` suffix and
     * `ContributorTag` is the single letter `p` -- so the guard against the
     * template's other failure mode, a copied `TAG_NAME`, is that every parse
     * refuses all twenty-three arrays that are not its own.
     */
    @Test
    fun `no tag parses another tag's array`() {
        val assembled: List<Pair<String, Array<String>>> = listOf(
            ArtifactIdTag.TAG_NAME to ArtifactIdTag.assemble(eventId, relay, author),
            ArtifactVersionIdTag.TAG_NAME to ArtifactVersionIdTag.assemble(eventId, relay, author),
            ArtifactVersionMetadataTag.TAG_NAME to ArtifactVersionMetadataTag.assemble("v2"),
            ChapterIdTag.TAG_NAME to ChapterIdTag.assemble(eventId, relay, author),
            ChunkIdTag.TAG_NAME to ChunkIdTag.assemble(eventId, relay, author),
            ContributorTag.TAG_NAME to ContributorTag.assemble(author, ContributorTag.ROLE_EDITOR),
            CountryTag.TAG_NAME to CountryTag.assemble("Botswana"),
            CreatorTag.TAG_NAME to CreatorTag.assemble(author),
            DialectIdTag.TAG_NAME to DialectIdTag.assemble(eventId, relay, author),
            IndexTag.TAG_NAME to IndexTag.assemble(7),
            LanguageTag.TAG_NAME to LanguageTag.assemble("tn"),
            LicenseTag.TAG_NAME to LicenseTag.assemble("CC-BY-SA-4.0"),
            NameTag.TAG_NAME to NameTag.assemble("Gospel of John"),
            OriginalTextTag.TAG_NAME to OriginalTextTag.assemble("In the beginning was the Word"),
            PayloadIdTag.TAG_NAME to PayloadIdTag.assemble(eventId, relay, author),
            PayloadKindTag.TAG_NAME to PayloadKindTag.assemble(30303),
            ProjectIdTag.TAG_NAME to ProjectIdTag.assemble(eventId, relay, author),
            TranslationArtifactVersionIdTag.TAG_NAME to
                TranslationArtifactVersionIdTag.assemble(eventId, relay, author),
            TranslationChapterIdTag.TAG_NAME to
                TranslationChapterIdTag.assemble(eventId, relay, author),
            TranslationChunkIdTag.TAG_NAME to
                TranslationChunkIdTag.assemble(eventId, relay, author),
            TranslationIdTag.TAG_NAME to TranslationIdTag.assemble(eventId, relay, author),
            UrlTag.TAG_NAME to UrlTag.assemble("https://mantra.press/john"),
            VisibilityTag.TAG_NAME to VisibilityTag.assemble("private"),
            WordStatisticsTag.TAG_NAME to WordStatisticsTag.assemble(412, 2317),
        )

        val parsers: List<Pair<String, (Array<String>) -> Any?>> = listOf(
            ArtifactIdTag.TAG_NAME to ArtifactIdTag::parse,
            ArtifactVersionIdTag.TAG_NAME to ArtifactVersionIdTag::parse,
            ArtifactVersionMetadataTag.TAG_NAME to ArtifactVersionMetadataTag::parse,
            ChapterIdTag.TAG_NAME to ChapterIdTag::parse,
            ChunkIdTag.TAG_NAME to ChunkIdTag::parse,
            ContributorTag.TAG_NAME to ContributorTag::parse,
            CountryTag.TAG_NAME to CountryTag::parse,
            CreatorTag.TAG_NAME to CreatorTag::parse,
            DialectIdTag.TAG_NAME to DialectIdTag::parse,
            IndexTag.TAG_NAME to IndexTag::parse,
            LanguageTag.TAG_NAME to LanguageTag::parse,
            LicenseTag.TAG_NAME to LicenseTag::parse,
            NameTag.TAG_NAME to NameTag::parse,
            OriginalTextTag.TAG_NAME to OriginalTextTag::parse,
            PayloadIdTag.TAG_NAME to PayloadIdTag::parse,
            PayloadKindTag.TAG_NAME to PayloadKindTag::parse,
            ProjectIdTag.TAG_NAME to ProjectIdTag::parse,
            TranslationArtifactVersionIdTag.TAG_NAME to TranslationArtifactVersionIdTag::parse,
            TranslationChapterIdTag.TAG_NAME to TranslationChapterIdTag::parse,
            TranslationChunkIdTag.TAG_NAME to TranslationChunkIdTag::parse,
            TranslationIdTag.TAG_NAME to TranslationIdTag::parse,
            UrlTag.TAG_NAME to UrlTag::parse,
            VisibilityTag.TAG_NAME to VisibilityTag::parse,
            WordStatisticsTag.TAG_NAME to WordStatisticsTag::parse,
        )

        // Twenty-four names, each one its own. Two tags sharing a name would make
        // the cross-check below vacuous for the pair, so count them first.
        assertEquals(24, assembled.size)
        assertEquals(assembled.size, assembled.map { it.first }.toSet().size)
        assertEquals(assembled.map { it.first }, parsers.map { it.first })

        for ((name, parse) in parsers) {
            for ((otherName, otherTag) in assembled) {
                if (otherName == name) continue
                assertNull(parse(otherTag), "$name: parse accepted $otherName's tag")
            }
        }
    }
}
