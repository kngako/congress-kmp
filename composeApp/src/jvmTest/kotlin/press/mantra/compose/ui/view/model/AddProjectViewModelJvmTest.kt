package press.mantra.compose.ui.view.model

import androidx.compose.foundation.text.input.TextFieldState
import androidx.room3.Room
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import press.mantra.compose.database.MantraDatabase
import press.mantra.compose.database.builder.getRoomDatabase
import press.mantra.compose.database.repository.DatabaseChatRepository
import press.mantra.compose.database.repository.DatabaseMantraRepository
import press.mantra.compose.nostr.nip30303.ProjectArtifactVersionEvent
import press.mantra.compose.nostr.nip30303.ProjectEvent
import press.mantra.compose.nostr.nip30303.ProjectTranslationArtifactVersionEvent
import press.mantra.compose.repository.NostrRepository
import press.mantra.compose.ui.view.state.AddProjectUIState
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * What the form actually puts in front of the group.
 *
 * A project and its first contents go out as one batch whose memberships carry
 * the project's id -- and that id is a hash over the group's key at the room's
 * derivation path, so it does not exist until the proposal is made. The screen
 * therefore hands over a *function* of the signed lead rather than a list, and
 * nothing downstream would notice if that function returned the wrong thing: a
 * membership naming the wrong project is a valid event, signs cleanly, and
 * lands as a row pointing somewhere else.
 *
 * So these assert the batch's shape against a recording repository that signs
 * the lead the way a finished session does. Three properties, each of which has
 * a silent failure behind it:
 *
 *  - the memberships name the project that was signed, not one worked out
 *    beforehand;
 *  - both halves are in the batch, since the two lists come from two different
 *    `membershipsOf` calls and dropping one is a dropped selection;
 *  - the creator on the event is the member filling the form in, because there
 *    is no field for it and nothing else to check it against.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddProjectViewModelJvmTest {

    private val db: MantraDatabase = getRoomDatabase(
        Room.inMemoryDatabaseBuilder<MantraDatabase>()
    )
    private val room = ProjectRoom(db)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private val scope = CoroutineScope(UnconfinedTestDispatcher())

    private fun viewModel(signing: RecordingFrostSigningRepository) = AddProjectViewModel(
        chatRoomId = room.roomId,
        activeUserPublicKey = room.member,
        relayHint = null,
        initialAddProjectUIState = AddProjectUIState.Loading,
        nostrRepository = NostrRepository.NO_OP_NOSTR_REPOSITORY,
        chatRepository = DatabaseChatRepository(db, scope),
        mantraRepository = DatabaseMantraRepository(db, scope),
        frostSigningRepository = signing,
    )

    /** Opens the form and waits for the room and library to land. */
    private suspend fun loaded(signing: RecordingFrostSigningRepository): Pair<AddProjectViewModel, AddProjectUIState.Loaded> {
        val viewModel = viewModel(signing)
        viewModel.initiateAddProject()
        return viewModel to awaitValue {
            viewModel.addProjectUIState as? AddProjectUIState.Loaded
        }
    }

    /**
     * Proposes, and waits for whichever callback the view model reaches.
     *
     * Both fire on `Dispatchers.Main` from inside a `Dispatchers.IO` launch, so
     * `addProject` returns long before either runs.
     */
    private suspend fun propose(
        viewModel: AddProjectViewModel,
        state: AddProjectUIState.Loaded,
        name: String = "Gospel of John into Sesotho",
        description: String = "The four chapters.",
        owners: Set<String> = setOf(room.roomId),
        sourceVersionIds: List<String> = emptyList(),
        translationIds: List<String> = emptyList(),
    ): String? {
        val outcome = CompletableDeferred<String?>()

        viewModel.addProject(
            localChatRoom = state.localChatRoom,
            nameField = TextFieldState(name),
            descriptionField = TextFieldState(description),
            ownerPublicKeys = owners,
            sourceVersionIds = sourceVersionIds,
            translationIds = translationIds,
            onSuccess = { outcome.complete(it) },
            onFailure = { outcome.complete(null) }
        )

        return withTimeout(10_000) { outcome.await() }
    }

    // --- what the form offers ---

    @Test
    fun `the form offers every version in the library and every translation`() = runBlocking {
        room.seed()
        val signing = RecordingFrostSigningRepository(room.roomId)
        val (_, state) = loaded(signing)

        // Versions have no by-room query -- they hang off an artifact -- so this
        // is the walk down the tree, and a walk that stopped at the first
        // artifact would look like a library with one version in it.
        assertEquals(
            setOf(room.versionId, room.secondVersionId),
            state.sourceVersions.map { it.id }.toSet()
        )
        assertEquals(listOf(room.translationId), state.translations.map { it.id })
        assertTrue(state.canSign)
    }

    // --- what goes to the group ---

    @Test
    fun `the batch is the project, then its sources, then its translations`() = runBlocking {
        room.seed()
        val signing = RecordingFrostSigningRepository(room.roomId)
        val (viewModel, state) = loaded(signing)

        val sessionId = propose(
            viewModel, state,
            sourceVersionIds = listOf(room.versionId, room.secondVersionId),
            translationIds = listOf(room.translationId),
        )

        assertEquals("session-1", sessionId)

        assertEquals(
            listOf(
                ProjectEvent.KIND,
                ProjectArtifactVersionEvent.KIND,
                ProjectArtifactVersionEvent.KIND,
                ProjectTranslationArtifactVersionEvent.KIND,
            ),
            signing.proposed.map { it.kind },
            "the project leads, because the memberships are derived from its id"
        )
    }

    @Test
    fun `every membership names the project the session signed`() = runBlocking {
        room.seed()
        val signing = RecordingFrostSigningRepository(room.roomId)
        val (viewModel, state) = loaded(signing)

        assertEquals(
            "session-1",
            propose(
                viewModel, state,
                description = "",
                sourceVersionIds = listOf(room.versionId),
                translationIds = listOf(room.translationId),
            )
        )

        val project = signing.proposed.first()

        // The point of the whole lead-and-dependents shape. Nothing else checks
        // this: a membership naming some other id is a valid signed event that
        // lands as a row pointing at nothing.
        val source = signing.proposed.single { it.kind == ProjectArtifactVersionEvent.KIND }
            .let {
                ProjectArtifactVersionEvent(
                    it.id, it.pubKey, it.createdAt, it.tags, it.content, it.sig
                )
            }
        assertEquals(project.id, source.projectId())
        assertEquals(room.versionId, source.artifactVersionId())

        val translation = signing.proposed
            .single { it.kind == ProjectTranslationArtifactVersionEvent.KIND }
            .let {
                ProjectTranslationArtifactVersionEvent(
                    it.id, it.pubKey, it.createdAt, it.tags, it.content, it.sig
                )
            }
        assertEquals(project.id, translation.projectId())
        assertEquals(room.translationId, translation.translationArtifactVersionId())

        // And they read as one act rather than four events sharing a session.
        assertTrue(signing.proposed.all { it.createdAt == project.createdAt })
    }

    @Test
    fun `the creator is the member filling the form in, and the owners are what was chosen`() =
        runBlocking {
            room.seed()
            val signing = RecordingFrostSigningRepository(room.roomId)
            val (viewModel, state) = loaded(signing)

            assertEquals(
                "session-1",
                propose(viewModel, state, owners = setOf(room.roomId, room.otherMember)),
                "a project with no contents is still a project"
            )

            val project = signing.proposed.single().let {
                ProjectEvent(it.id, it.pubKey, it.createdAt, it.tags, it.content, it.sig)
            }

            // There is no creator field, so this is the only thing that says the
            // right person is credited for what the group is about to sign.
            assertEquals(room.member, project.creator())
            assertEquals(setOf(room.roomId, room.otherMember), project.owners().toSet())
            assertEquals("Gospel of John into Sesotho", project.name())
            assertEquals("The four chapters.", project.content)

            // The author is the room, not the creator: the group signs it.
            assertEquals(room.roomId, signing.proposed.single().pubKey)
        }

    @Test
    fun `a project with no contents proposes one event`() = runBlocking {
        room.seed()
        val signing = RecordingFrostSigningRepository(room.roomId)
        val (viewModel, state) = loaded(signing)

        assertEquals(
            "session-1",
            propose(viewModel, state, description = ""),
            "an empty project is a name waiting for work"
        )

        assertEquals(listOf(ProjectEvent.KIND), signing.proposed.map { it.kind })
    }

    // --- what the form refuses to send ---

    @Test
    fun `a blank name proposes nothing`() = runBlocking {
        room.seed()
        val signing = RecordingFrostSigningRepository(room.roomId)
        val (viewModel, state) = loaded(signing)

        assertNull(
            propose(viewModel, state, name = "   "),
            "a nameless project should not reach a quorum"
        )
        assertTrue(signing.proposed.isEmpty())
    }

    @Test
    fun `an empty owner set proposes nothing rather than throwing`() = runBlocking {
        room.seed()
        val signing = RecordingFrostSigningRepository(room.roomId)
        val (viewModel, state) = loaded(signing)

        // `ProjectEvent.build` throws on an empty set, so the view model has to
        // refuse first. The form disables the last remove, but the guard is what
        // makes that a safety rather than the only thing standing in the way.
        assertNull(
            propose(viewModel, state, description = "", owners = emptySet()),
            "a project with no owner cannot be built"
        )
        assertTrue(signing.proposed.isEmpty())
    }

    @Test
    fun `a room that cannot be found is an error rather than an empty form`() = runBlocking {
        // Nothing seeded.
        val viewModel = viewModel(RecordingFrostSigningRepository(room.roomId))

        viewModel.initiateAddProject()

        val error = awaitValue { viewModel.addProjectUIState as? AddProjectUIState.Error }

        assertTrue(error.message.isNotBlank(), "the error should say something")
    }
}
