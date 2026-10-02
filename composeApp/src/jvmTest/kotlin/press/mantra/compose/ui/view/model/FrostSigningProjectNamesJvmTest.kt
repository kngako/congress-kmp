package press.mantra.compose.ui.view.model

import androidx.room3.Room
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import press.mantra.compose.database.MantraDatabase
import press.mantra.compose.database.builder.getRoomDatabase
import press.mantra.compose.database.model.FrostSigningItem
import press.mantra.compose.database.model.FrostSigningSession
import press.mantra.compose.database.model.FrostSignerMessage
import press.mantra.compose.database.repository.DatabaseChatRepository
import press.mantra.compose.database.repository.DatabaseMantraRepository
import press.mantra.compose.nostr.nip30303.ProjectArtifactVersionEvent
import press.mantra.compose.nostr.nip30303.ProjectEvent
import press.mantra.compose.nostr.nip30303.ProjectTranslationArtifactVersionEvent
import press.mantra.compose.repository.FrostSigningRepository
import press.mantra.compose.repository.NostrRepository
import press.mantra.compose.text.ProposedEvent
import press.mantra.compose.ui.view.state.FrostSigningUIState
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a member is actually shown before they sign something about a project.
 *
 * A membership event carries two 64-character ids and nothing else. Unresolved,
 * the signing screen asks somebody to agree to "a4f1be55d0c3" -- which is not a
 * question anybody can answer, and it is the whole reason
 * `FrostSigningViewModel` resolves names before handing `ProposedEvent.summarize`
 * a lookup.
 *
 * Nothing downstream notices if that resolution is wrong or missing. The summary
 * still renders, the session still signs, and the member still taps approve.
 * These tests are the only thing standing between the resolver and a screen that
 * quietly goes back to showing hashes:
 *
 *  - a membership resolves to the artifact and label a reader recognises;
 *  - a project's creator and outside owners resolve to people;
 *  - the room's own key is *not* named as an owner, because saying "this group"
 *    on every project teaches a member to skip the line that matters;
 *  - an id this device has never seen falls back to a short hash rather than
 *    inventing a name or dropping the line.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FrostSigningProjectNamesJvmTest {

    private val db: MantraDatabase = getRoomDatabase(
        Room.inMemoryDatabaseBuilder<MantraDatabase>()
    )
    private val room = ProjectRoom(db)
    private val projectId = "p".repeat(64)
    private val sessionId = "session-1"

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private val scope = CoroutineScope(UnconfinedTestDispatcher())

    /** A session whose items are [events], which is all this screen reads. */
    private class StubSigning(
        private val chatRoomId: String,
        private val events: List<Event>,
        private val delegate: FrostSigningRepository =
            FrostSigningRepository.NO_OP_FROST_SIGNING_REPOSITORY,
    ) : FrostSigningRepository by delegate {

        override fun observeSessionById(sessionId: String): Flow<FrostSigningSession?> = flowOf(
            FrostSigningSession(
                id = sessionId,
                chatRoomId = chatRoomId,
                coordinatorPublicKey = chatRoomId,
                userPublicKey = chatRoomId,
                dkgSessionId = "ceremony-1",
                threshold = 2,
                participantCount = 3,
                signerId = 0,
            )
        )

        override fun observeMessages(sessionId: String): Flow<List<FrostSignerMessage>> =
            flowOf(emptyList())

        override fun observeItems(sessionId: String): Flow<List<FrostSigningItem>> = flowOf(
            events.mapIndexed { index, event ->
                FrostSigningItem(
                    sessionId = sessionId,
                    itemIndex = index,
                    unsignedEventJson = event.toJson(),
                    eventId = event.id,
                    nonceRandom = "a".repeat(64),
                )
            }
        )
    }

    private fun signed(template: EventTemplate<*>) = Event(
        id = EventHasher.hashId(
            pubKey = room.roomId,
            createdAt = template.createdAt,
            kind = template.kind,
            tags = template.tags,
            content = template.content,
        ),
        pubKey = room.roomId,
        createdAt = template.createdAt,
        kind = template.kind,
        tags = template.tags,
        content = template.content,
        sig = "f".repeat(128),
    )

    /**
     * The names the signing screen would be handed for [events].
     *
     * The profile source is a real read of the seeded database rather than the
     * no-op, whose `getProfileWithPublicKey` is a `TODO()` -- so a resolver that
     * asks it about anybody throws on `Dispatchers.IO`, which surfaces as this
     * helper timing out rather than as a failure anybody can read. Reading the
     * database gives the honest answer for a stranger too: null.
     */
    private suspend fun namesFor(events: List<Event>): Map<String, String> {
        val viewModel = FrostSigningViewModel(
            chatRoomId = room.roomId,
            sessionId = sessionId,
            activeUserPublicKey = room.member,
            initialFrostSigningUIState = FrostSigningUIState.Loading,
            chatRepository = DatabaseChatRepository(db, scope),
            frostSigningRepository = StubSigning(room.roomId, events),
            mantraRepository = DatabaseMantraRepository(db, scope),
            nostrRepository = ProfileReadingNostrRepository(db),
        )

        viewModel.initiate()

        return awaitValue {
            (viewModel.frostSigningUIState as? FrostSigningUIState.Loaded)
                ?.takeIf { it.items.isNotEmpty() }
                ?.names
        }
    }

    private fun membership(versionId: String) = signed(
        ProjectArtifactVersionEvent.build(
            projectId = projectId,
            artifactVersionId = versionId,
            createdAt = 1_700_000_000,
        )
    )

    // --- a membership proposed on its own ---

    @Test
    fun `a membership resolves to the artifact and the label`() = runBlocking {
        room.seed()
        room.project(id = projectId)

        val names = namesFor(listOf(membership(room.versionId)))

        assertEquals("${room.artifactName} ${room.versionLabel}", names[room.versionId])
        assertEquals("Gospel of John", names[projectId])
    }

    @Test
    fun `and so the summary a member reads is words rather than a hash`() = runBlocking {
        room.seed()
        room.project(id = projectId)

        val event = membership(room.versionId)
        val names = namesFor(listOf(event))

        // The end the resolver exists for, asserted through the same call the
        // signing screen makes.
        val summary = ProposedEvent.summarize(
            event = event,
            chatRoomId = room.roomId,
            resolve = names::get,
        )

        assertEquals("A source version joins the project", summary.label)
        assertEquals("To Kill a Mockingbird 1.0 · to Gospel of John", summary.detail)
        assertTrue(
            !summary.detail.contains(room.versionId.take(12)),
            "a resolved membership should not still be showing a hash"
        )
    }

    @Test
    fun `a translation membership resolves to the translation's name`() = runBlocking {
        room.seed()
        room.project(id = projectId)

        val event = signed(
            ProjectTranslationArtifactVersionEvent.build(
                projectId = projectId,
                translationArtifactVersionId = room.translationId,
                createdAt = 1_700_000_000,
            )
        )

        assertEquals(room.translationName, namesFor(listOf(event))[room.translationId])
    }

    // --- what cannot be resolved ---

    @Test
    fun `a version this device has never seen is left to the hash`() = runBlocking {
        room.seed()
        room.project(id = projectId)
        val absent = "9".repeat(64)

        val event = membership(absent)
        val names = namesFor(listOf(event))

        // Left out rather than guessed at. The device has not seen the thing,
        // and a short hash says so; inventing a name would be worse than saying
        // nothing.
        assertTrue(absent !in names)

        val summary = ProposedEvent.summarize(event, room.roomId, names::get)
        assertEquals(absent.take(12) + " · to Gospel of John", summary.detail)
    }

    @Test
    fun `a project being signed in the same batch resolves to nothing`() = runBlocking {
        room.seed()
        // No project row: this is the founding batch, where the project is on the
        // line above and nothing has applied it yet.
        val project = signed(
            ProjectEvent.build(
                name = "Gospel of John into Sesotho",
                description = "",
                creatorPublicKey = room.member,
                ownerPublicKeys = setOf(room.roomId),
                createdAt = 1_700_000_000,
            )
        )
        val source = signed(
            ProjectArtifactVersionEvent.build(
                projectId = project.id,
                artifactVersionId = room.versionId,
                createdAt = 1_700_000_000,
            )
        )

        val names = namesFor(listOf(project, source))

        assertTrue(
            project.id !in names,
            "nothing has applied the project yet, which is why a membership says the version first"
        )
        // And the version still resolves, which is what makes the batch readable.
        assertEquals("${room.artifactName} ${room.versionLabel}", names[room.versionId])
    }

    // --- the people on a project ---

    @Test
    fun `a project's creator resolves to a profile`() = runBlocking {
        room.seed()

        // NostrRepository is the no-op here, so prove the wiring with a stub
        // that answers for the member and nobody else.
        val names = namesFor(
            listOf(
                signed(
                    ProjectEvent.build(
                        name = "Gospel of John into Sesotho",
                        description = "",
                        creatorPublicKey = room.member,
                        ownerPublicKeys = setOf(room.roomId, room.otherMember),
                        createdAt = 1_700_000_000,
                    )
                )
            )
        )

        assertEquals("kgothatso", names[room.member])
    }

    @Test
    fun `an owner with no profile is left to the hash, and the room is left out of the line`() =
        runBlocking {
            room.seed()

            val project = signed(
                ProjectEvent.build(
                    name = "Gospel of John into Sesotho",
                    description = "",
                    creatorPublicKey = room.member,
                    ownerPublicKeys = setOf(room.roomId, room.otherMember),
                    createdAt = 1_700_000_000,
                )
            )
            val names = namesFor(listOf(project))

            assertTrue(room.otherMember !in names, "a stranger has no name to show")

            val summary = ProposedEvent.summarize(project, room.roomId, names::get)

            assertTrue(summary.detail.contains("started by kgothatso"), summary.detail)
            assertTrue(
                summary.detail.contains("owned by ${room.otherMember.take(12)}"),
                summary.detail
            )
            assertTrue(
                !summary.detail.contains(room.roomId.take(12)),
                "the room owning its own work is the ordinary case and says nothing"
            )
        }

    /** Only the one method the resolver reaches for. */
    private class ProfileReadingNostrRepository(
        private val db: MantraDatabase,
        private val delegate: NostrRepository = NostrRepository.NO_OP_NOSTR_REPOSITORY,
    ) : NostrRepository by delegate {
        override suspend fun getProfileWithPublicKey(publicKey: String) =
            db.profileDao().getProfileByPublicKey(publicKey)
    }
}
