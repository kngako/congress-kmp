package press.mantra.compose.managers

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import press.mantra.compose.database.model.FrostSigningItem
import press.mantra.compose.database.model.FrostSigningSession
import press.mantra.compose.database.model.MantraDialect
import press.mantra.compose.database.model.intermdiate.LocalFrostSigningSession
import press.mantra.compose.database.model.types.FrostSigningStage
import press.mantra.compose.language.AfricanLanguage
import press.mantra.compose.nostr.nip30303.ChapterEvent
import press.mantra.compose.nostr.nip30303.DialectEvent

/**
 * The dialects an open signing session is already signing.
 *
 * This is the half of the identity rule that rows cannot answer. Two admins open
 * the picker, both see the same forty missing and both propose: without reading
 * what is in flight the room gets eighty dialects, forty of them duplicates that
 * nothing will ever merge. `FrostSigningRepository.unsignedEvents` makes a
 * batch readable at proposal time, which is exactly when it has to be.
 */
class DialectsInFlightTest {

    private val groupKey = "a".repeat(64)
    private val chatRoomId = "r".repeat(64)

    private val sesotho = AfricanLanguage("Sesotho", "st", "Lesotho")
    private val swahiliKenya = AfricanLanguage("Kiswahili", "sw", "Kenya")
    private val swahiliTanzania = AfricanLanguage("Kiswahili", "sw", "Tanzania")

    private val catalogue = listOf(sesotho, swahiliKenya, swahiliTanzania)

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

    /** What the repository does, so the lambda under test is the real one. */
    private val unsignedEvents: (List<FrostSigningItem>) -> List<Event> =
        { items -> items.map { Event.fromJson(it.unsignedEventJson) } }

    private fun session(
        id: String,
        stage: FrostSigningStage,
        json: List<String>,
    ) = LocalFrostSigningSession(
        session = FrostSigningSession(
            id = id,
            chatRoomId = chatRoomId,
            coordinatorPublicKey = "c".repeat(64),
            userPublicKey = "u".repeat(64),
            dkgSessionId = "d".repeat(64),
            threshold = 2,
            participantCount = 3,
            signerId = 1,
            stage = stage,
        ),
        unorderedItems = json.mapIndexed { index, eventJson ->
            FrostSigningItem(
                sessionId = id,
                itemIndex = index,
                unsignedEventJson = eventJson,
                eventId = "e$index".padEnd(64, 'f').take(64),
                nonceRandom = "n$index".padEnd(64, 'f').take(64),
            )
        },
    )

    private fun dialectJson(language: AfricanLanguage) = unsigned(
        DialectScaffold.dialectsOf(listOf(language), createdAt = 1_700_000_000L).single()
    ).toJson()

    private fun waveFor(
        languages: List<AfricanLanguage>,
        stage: FrostSigningStage = FrostSigningStage.COLLECTING_NONCES,
        id: String = "s".repeat(64),
    ) = session(id = id, stage = stage, json = languages.map(::dialectJson))

    @Test
    fun anOpenSessionsDialectsArePending() {
        assertEquals(
            listOf(sesotho, swahiliKenya),
            DialectScaffold.pendingIn(
                sessions = listOf(waveFor(listOf(sesotho, swahiliKenya))),
                unsignedEvents = unsignedEvents,
                catalogue = catalogue,
            )
        )
    }

    @Test
    fun aSessionCollectingSignaturesIsStillOpen() {
        assertEquals(
            listOf(sesotho),
            DialectScaffold.pendingIn(
                sessions = listOf(
                    waveFor(
                        listOf(sesotho),
                        stage = FrostSigningStage.COLLECTING_PARTIAL_SIGNATURES
                    )
                ),
                unsignedEvents = unsignedEvents,
                catalogue = catalogue,
            )
        )
    }

    /** It left nothing behind, so it has to be proposable again. */
    @Test
    fun aFailedSessionsDialectsAreNotPending() {
        assertEquals(
            emptyList(),
            DialectScaffold.pendingIn(
                sessions = listOf(waveFor(listOf(sesotho), stage = FrostSigningStage.FAILED)),
                unsignedEvents = unsignedEvents,
                catalogue = catalogue,
            )
        )
    }

    /**
     * Its dialects are rows by now, so counting them here would hide them behind
     * "waiting for the group" rather than "already yours".
     */
    @Test
    fun aCompleteSessionsDialectsAreNotPending() {
        assertEquals(
            emptyList(),
            DialectScaffold.pendingIn(
                sessions = listOf(waveFor(listOf(sesotho), stage = FrostSigningStage.COMPLETE)),
                unsignedEvents = unsignedEvents,
                catalogue = catalogue,
            )
        )
    }

    @Test
    fun twoOpenWavesAreReadTogether() {
        assertEquals(
            listOf(sesotho, swahiliTanzania),
            DialectScaffold.pendingIn(
                sessions = listOf(
                    waveFor(listOf(sesotho), id = "1".repeat(64)),
                    waveFor(listOf(swahiliTanzania), id = "2".repeat(64)),
                ),
                unsignedEvents = unsignedEvents,
                catalogue = catalogue,
            )
        )
    }

    @Test
    fun aSessionSigningSomethingElseContributesNothing() {
        val chapter = unsigned(
            ChapterEvent.build(
                artifactVersionId = "b".repeat(64),
                name = "Chapter 1",
                originalText = "Paragraph.",
                index = 0,
                wordCount = 1,
                characterCount = 10,
            )
        ).toJson()

        assertEquals(
            emptyList(),
            DialectScaffold.pendingIn(
                sessions = listOf(
                    session(
                        id = "s".repeat(64),
                        stage = FrostSigningStage.COLLECTING_NONCES,
                        json = listOf(chapter),
                    )
                ),
                unsignedEvents = unsignedEvents,
                catalogue = catalogue,
            )
        )
    }

    @Test
    fun aDialectTheCatalogueDoesNotListIsNotReported() {
        assertEquals(
            emptyList(),
            DialectScaffold.pendingIn(
                sessions = listOf(waveFor(listOf(AfricanLanguage("Nheengatu", "yrl", "Brazil")))),
                unsignedEvents = unsignedEvents,
                catalogue = catalogue,
            )
        )
    }

    /**
     * A damaged session must not take the picker down with it, and the other
     * session's dialects still have to be read. See the trade named on
     * `pendingIn`: its own dialects look selectable again, which is recoverable,
     * and a screen that will not open is not.
     */
    @Test
    fun anUnreadableSessionIsSkippedRatherThanThrown() {
        assertEquals(
            listOf(swahiliKenya),
            DialectScaffold.pendingIn(
                sessions = listOf(
                    session(
                        id = "1".repeat(64),
                        stage = FrostSigningStage.COLLECTING_NONCES,
                        json = listOf("not an event"),
                    ),
                    waveFor(listOf(swahiliKenya), id = "2".repeat(64)),
                ),
                unsignedEvents = unsignedEvents,
                catalogue = catalogue,
            )
        )
    }

    @Test
    fun noSessionsMeansNothingPending() {
        assertEquals(
            emptyList(),
            DialectScaffold.pendingIn(
                sessions = emptyList(),
                unsignedEvents = unsignedEvents,
                catalogue = catalogue,
            )
        )
    }

    /** The two halves together, which is how the picker reads them. */
    @Test
    fun missingExcludesBothWhatIsHeldAndWhatIsInFlight() {
        val pending = DialectScaffold.pendingIn(
            sessions = listOf(waveFor(listOf(swahiliKenya))),
            unsignedEvents = unsignedEvents,
            catalogue = catalogue,
        )

        assertEquals(
            listOf(swahiliTanzania),
            DialectScaffold.missingFrom(
                existing = listOf(
                    MantraDialect(
                        id = "d".repeat(64),
                        publicKey = groupKey,
                        name = "Sesotho",
                        country = "Lesotho",
                        language = "st",
                        signature = "",
                        chatRoomId = chatRoomId,
                    )
                ),
                pending = pending,
                catalogue = catalogue,
            )
        )
    }
}
