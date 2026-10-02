package press.mantra.compose.ui.view.model

import androidx.room3.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import press.mantra.compose.database.MantraDatabase
import press.mantra.compose.database.builder.getRoomDatabase
import press.mantra.compose.database.model.MantraArtifact
import press.mantra.compose.database.model.MantraDialect
import press.mantra.compose.database.model.MantraTranslationArtifactVersion
import press.mantra.compose.database.repository.DatabaseChatRepository
import press.mantra.compose.database.repository.DatabaseMantraRepository
import press.mantra.compose.ui.view.state.DialectDetailUIState
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What a dialect's screen is looking at, and what it leaves out.
 *
 * The lists are read by the dialect's id alone -- no room is named beside it,
 * because `dialectId` is a foreign key into this room's own rows and a second
 * filter would restate a constraint the key already enforces. So the case worth
 * having is a room holding *two* dialects: if the query ever drifted to scoping
 * by room instead, every test with one dialect in it would still pass and this
 * one would not.
 *
 * The other half is that a missing dialect is an error rather than an empty
 * screen. "This room has written nothing in Sesotho" and "there is no such
 * dialect here" would otherwise look identical, and only one of them is worth
 * offering a retry for.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DialectDetailJvmTest {

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

    private val otherDialectId = "9".repeat(64)
    private val unusedDialectId = "d".repeat(64)

    private fun viewModel(dialectId: String = room.dialectId) = DialectDetailViewModel(
        dialectId = dialectId,
        chatRoomId = room.roomId,
        activeUserPublicKey = room.member,
        relayHint = null,
        initialDialectDetailUIState = DialectDetailUIState.Loading,
        mantraRepository = DatabaseMantraRepository(db, scope),
        chatRepository = DatabaseChatRepository(db, scope),
    )

    private suspend fun loaded(dialectId: String = room.dialectId): DialectDetailUIState.Loaded {
        val viewModel = viewModel(dialectId)
        viewModel.initiateDialectDetail()
        return awaitValue { viewModel.dialectDetailUIState as? DialectDetailUIState.Loaded }
    }

    /** A second dialect in the same room, with its own artifact and translation. */
    private suspend fun seedSecondDialect() {
        db.mantraDialectDao().upsert(
            MantraDialect(
                id = otherDialectId,
                publicKey = room.roomId,
                name = "Setswana",
                country = "BW",
                language = "tn",
                signature = room.signature,
                chatRoomId = room.roomId,
            )
        )
        db.mantraArtifactDao().upsert(
            MantraArtifact(
                id = "8".repeat(64),
                publicKey = room.roomId,
                name = "Another book",
                url = "https://example.invalid/another",
                visibility = "private",
                dialectId = otherDialectId,
                license = "cc",
                chatRoomId = room.roomId,
                signature = room.signature,
            )
        )
        db.mantraTranslationArtifactVersionDao().upsert(
            MantraTranslationArtifactVersion(
                id = "4".repeat(64),
                publicKey = room.roomId,
                artifactVersionId = room.versionId,
                dialectId = otherDialectId,
                name = "Setswana Mockingbird",
                visibility = "private",
                license = "cc",
                signature = room.signature,
                chatRoomId = room.roomId,
            )
        )
    }

    @Test
    fun `the dialect loads with what is written in it`() = runBlocking {
        room.seed()

        val state = loaded()

        assertEquals("Sesotho", state.dialect.name)
        assertEquals("st", state.dialect.language)
        assertEquals(listOf(room.artifactId), state.artifacts.map { it.id })
        assertEquals(listOf(room.translationId), state.translations.map { it.id })
    }

    /**
     * The case that fails if the queries ever scope by room instead of by
     * dialect. Both dialects are in one room, so a room-scoped read returns all
     * four rows and reads as a working screen.
     */
    @Test
    fun `another dialect in the same room is left out`() = runBlocking {
        room.seed()
        seedSecondDialect()

        val sesotho = loaded()

        assertEquals(listOf(room.artifactId), sesotho.artifacts.map { it.id })
        assertEquals(listOf(room.translationId), sesotho.translations.map { it.id })

        val setswana = loaded(otherDialectId)

        assertEquals(listOf("8".repeat(64)), setswana.artifacts.map { it.id })
        assertEquals(listOf("4".repeat(64)), setswana.translations.map { it.id })
    }

    /**
     * The empty state, which is a normal state and not a failure: a room defines
     * a dialect before it has anything in it, and that is the whole point of
     * being able to define one.
     */
    @Test
    fun `a dialect nothing is written in loads empty rather than failing`() = runBlocking {
        room.seed()
        db.mantraDialectDao().upsert(
            MantraDialect(
                id = unusedDialectId,
                publicKey = room.roomId,
                name = "isiZulu",
                country = "ZA",
                language = "zu",
                signature = room.signature,
                chatRoomId = room.roomId,
            )
        )

        val state = loaded(unusedDialectId)

        assertEquals("isiZulu", state.dialect.name)
        assertTrue(state.artifacts.isEmpty())
        assertTrue(state.translations.isEmpty())
    }

    @Test
    fun `a dialect this device does not hold is an error, not an empty screen`() = runBlocking {
        room.seed()

        val viewModel = viewModel(dialectId = "c".repeat(64))
        viewModel.initiateDialectDetail()

        val state = awaitValue { viewModel.dialectDetailUIState as? DialectDetailUIState.Error }

        assertEquals("Couldn't find the dialect", state.message)
    }

    /**
     * The fixture's room has no MLS state, so no subgroup can be made from it --
     * a NIP-17 room has no admins to be a parent's. The flag says so instead of
     * the button failing when pressed.
     */
    @Test
    fun `a room with no group state cannot have a subgroup made for its dialect`() = runBlocking {
        room.seed()

        assertFalse(loaded().canAddSubgroup)
    }
}
