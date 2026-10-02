package press.mantra.compose.ui.view.model

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
import press.mantra.compose.database.model.MantraProjectArtifactVersion
import press.mantra.compose.database.model.MantraProjectTranslationArtifactVersion
import press.mantra.compose.database.repository.DatabaseChatRepository
import press.mantra.compose.database.repository.DatabaseMantraRepository
import press.mantra.compose.nostr.nip30303.ProjectArtifactVersionEvent
import press.mantra.compose.nostr.nip30303.ProjectTranslationArtifactVersionEvent
import press.mantra.compose.ui.view.state.AddToProjectUIState
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Which work the picker offers, and what one pick becomes.
 *
 * The filtering is the part that matters and the part nothing else would catch.
 * A membership that duplicates one the group has already signed is a *valid*
 * event with its own id -- the foreign keys are satisfied, the signature is
 * good, the apply arm writes the row -- so there is no layer below this that
 * says no. If the filter were wrong the only symptom would be a quorum spent
 * saying something the group has already said, which looks like the feature
 * working.
 *
 * The other half is that one pick is one event. The add-to flow knows the
 * project's id because the project exists, so it uses the flat batch rather
 * than lead-and-dependents, and a membership built against the wrong id would
 * land as a row pointing somewhere else.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddToProjectViewModelJvmTest {

    private val db: MantraDatabase = getRoomDatabase(
        Room.inMemoryDatabaseBuilder<MantraDatabase>()
    )
    private val room = ProjectRoom(db)
    private val projectId = "p".repeat(64)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private val scope = CoroutineScope(UnconfinedTestDispatcher())

    private fun viewModel(
        signing: RecordingFrostSigningRepository,
        translations: Boolean,
    ) = AddToProjectViewModel(
        projectId = projectId,
        chatRoomId = room.roomId,
        activeUserPublicKey = room.member,
        translations = translations,
        relayHint = null,
        initialAddToProjectUIState = AddToProjectUIState.Loading,
        chatRepository = DatabaseChatRepository(db, scope),
        mantraRepository = DatabaseMantraRepository(db, scope),
        frostSigningRepository = signing,
    )

    private suspend fun loaded(
        signing: RecordingFrostSigningRepository,
        translations: Boolean,
    ): Pair<AddToProjectViewModel, AddToProjectUIState.Loaded> {
        val viewModel = viewModel(signing, translations)
        viewModel.initiateAddToProject()
        return viewModel to awaitValue {
            viewModel.addToProjectUIState as? AddToProjectUIState.Loaded
        }
    }

    private suspend fun propose(
        viewModel: AddToProjectViewModel,
        state: AddToProjectUIState.Loaded,
        selectedId: String,
    ): String? {
        val outcome = CompletableDeferred<String?>()

        viewModel.addToProject(
            localChatRoom = state.localChatRoom,
            selectedId = selectedId,
            onSuccess = { outcome.complete(it) },
            onFailure = { outcome.complete(null) }
        )

        return withTimeout(10_000) { outcome.await() }
    }

    /** A membership already signed, so the picker has something to leave out. */
    private suspend fun alreadyHolds(versionId: String) =
        db.mantraProjectArtifactVersionDao().upsert(
            MantraProjectArtifactVersion(
                id = "m1".padEnd(64, '0'),
                publicKey = room.roomId,
                projectId = projectId,
                artifactVersionId = versionId,
                chatRoomId = room.roomId,
                signature = room.signature,
            )
        )

    // --- which work is offered ---

    @Test
    fun `the source picker offers every version in the library`() = runBlocking {
        room.seed()
        room.project(id = projectId)

        val (_, state) = loaded(RecordingFrostSigningRepository(room.roomId), translations = false)

        assertEquals(
            setOf(room.versionId, room.secondVersionId),
            state.candidates.map { it.id }.toSet()
        )
        assertTrue(state.canSign)
    }

    @Test
    fun `a version already in the project is not offered again`() = runBlocking {
        room.seed()
        room.project(id = projectId)
        alreadyHolds(room.versionId)

        val (_, state) = loaded(RecordingFrostSigningRepository(room.roomId), translations = false)

        // The whole point. Nothing below this refuses a duplicate: it is a valid
        // event with its own id, so it would cost a quorum to say what the group
        // has already said.
        assertEquals(listOf(room.secondVersionId), state.candidates.map { it.id })
    }

    @Test
    fun `a version is named by its artifact as well as its label`() = runBlocking {
        room.seed()
        room.project(id = projectId)

        val (_, state) = loaded(RecordingFrostSigningRepository(room.roomId), translations = false)

        val candidate = state.candidates.single { it.id == room.versionId }

        // "1.0" on its own is not a choice -- a room has several of those.
        assertEquals(room.versionLabel, candidate.label)
        assertEquals(room.artifactName, candidate.detail)
    }

    @Test
    fun `the translation picker offers translations, not versions`() = runBlocking {
        room.seed()
        room.project(id = projectId)

        val (_, state) = loaded(RecordingFrostSigningRepository(room.roomId), translations = true)

        assertEquals(listOf(room.translationId), state.candidates.map { it.id })
        assertEquals(room.translationName, state.candidates.single().label)
    }

    @Test
    fun `a translation already in the project is not offered again`() = runBlocking {
        room.seed()
        room.project(id = projectId)
        db.mantraProjectTranslationArtifactVersionDao().upsert(
            MantraProjectTranslationArtifactVersion(
                id = "m2".padEnd(64, '0'),
                publicKey = room.roomId,
                projectId = projectId,
                translationArtifactVersionId = room.translationId,
                chatRoomId = room.roomId,
                signature = room.signature,
            )
        )

        val (_, state) = loaded(RecordingFrostSigningRepository(room.roomId), translations = true)

        assertTrue(state.candidates.isEmpty())
    }

    @Test
    fun `the two halves filter independently`() = runBlocking {
        room.seed()
        room.project(id = projectId)
        // A source membership must not hide a translation, and the translation
        // this one names is of that very version -- which is what a filter
        // written against the wrong table would get wrong.
        alreadyHolds(room.versionId)

        val (_, translationState) =
            loaded(RecordingFrostSigningRepository(room.roomId), translations = true)

        assertEquals(listOf(room.translationId), translationState.candidates.map { it.id })
    }

    // --- what one pick becomes ---

    @Test
    fun `picking a source proposes one membership naming the project`() = runBlocking {
        room.seed()
        room.project(id = projectId)
        val signing = RecordingFrostSigningRepository(room.roomId)
        val (viewModel, state) = loaded(signing, translations = false)

        assertEquals("session-1", propose(viewModel, state, room.versionId))

        val proposed = signing.proposed.single()
        assertEquals(ProjectArtifactVersionEvent.KIND, proposed.kind)

        val membership = ProjectArtifactVersionEvent(
            proposed.id, proposed.pubKey, proposed.createdAt,
            proposed.tags, proposed.content, proposed.sig
        )
        assertEquals(projectId, membership.projectId())
        assertEquals(room.versionId, membership.artifactVersionId())
    }

    @Test
    fun `picking a translation proposes the translation kind`() = runBlocking {
        room.seed()
        room.project(id = projectId)
        val signing = RecordingFrostSigningRepository(room.roomId)
        val (viewModel, state) = loaded(signing, translations = true)

        assertEquals("session-1", propose(viewModel, state, room.translationId))

        val proposed = signing.proposed.single()
        assertEquals(ProjectTranslationArtifactVersionEvent.KIND, proposed.kind)

        val membership = ProjectTranslationArtifactVersionEvent(
            proposed.id, proposed.pubKey, proposed.createdAt,
            proposed.tags, proposed.content, proposed.sig
        )
        assertEquals(projectId, membership.projectId())
        assertEquals(room.translationId, membership.translationArtifactVersionId())
    }

    @Test
    fun `a session that will not open is reported rather than swallowed`() = runBlocking {
        room.seed()
        room.project(id = projectId)
        val signing = RecordingFrostSigningRepository(room.roomId).also { it.session = null }
        val (viewModel, state) = loaded(signing, translations = false)

        assertNull(propose(viewModel, state, room.versionId))
    }

    // --- what it refuses to open at all ---

    @Test
    fun `a project this device does not hold is an error`() = runBlocking {
        room.seed()
        // The room is there, the project is not.
        val viewModel = viewModel(RecordingFrostSigningRepository(room.roomId), translations = false)
        viewModel.initiateAddToProject()

        val error = awaitValue { viewModel.addToProjectUIState as? AddToProjectUIState.Error }

        assertTrue(error.message.isNotBlank())
    }
}
