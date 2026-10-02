package press.mantra.compose.ui.view.model

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import press.mantra.compose.database.model.ChatRoom
import press.mantra.compose.database.model.DkgSession
import press.mantra.compose.database.model.Participant
import press.mantra.compose.database.model.Profile
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.database.model.intermdiate.LocalParticipant
import press.mantra.compose.database.model.types.DkgApprovalStep
import press.mantra.compose.database.model.types.DkgRitualStage
import press.mantra.compose.repository.ChatRepository
import press.mantra.compose.repository.DkgRepository
import press.mantra.compose.ui.view.state.DkgRitualListUIState
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the key-ceremony list decides that nothing else can check.
 *
 * **Which ceremonies come first.** A room holds one per subgroup the group has
 * ever made plus its own, and the newest is routinely the finished one -- so
 * the list is only worth having if the two that want something from the reader
 * are at the top of it rather than wherever their dates put them.
 *
 * **What counts as wanting something.** Two different things do, owed by
 * different people: an approval gate this member cannot delegate, and a
 * finished ceremony whose room only its coordinator can create. An abandoned
 * ceremony wants neither, whatever the rows underneath it say.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DkgRitualListViewModelJvmTest {
    private val chatRoomId = "a1b2c3d4".repeat(8)
    private val me = "11".repeat(32)
    private val ada = "22".repeat(32)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the ceremonies waiting on this member are separated from the rest`() = runBlocking {
        val mine = session("mine", DkgRitualStage.COLLECTING_ROUND_1)
        val finished = session("finished", DkgRitualStage.COMPLETE)
        val subgroup = session("subgroup", DkgRitualStage.COLLECTING_HOST_KEYS)
            .copy(parentChatRoomId = "parent", subject = "Translators")

        val loaded = load(
            // Newest first, the way the query returns them -- and the one that
            // wants something is deliberately not the newest.
            sessions = listOf(finished, subgroup, mine),
            pending = mapOf(
                "mine" to DkgApprovalStep.ROUND_1,
                "subgroup" to DkgApprovalStep.HOST_KEY
            )
        )

        assertEquals(
            listOf("subgroup", "mine"),
            loaded.waitingForYou.map { it.session.id },
            "a ceremony waiting on this member was left down the list"
        )
        assertEquals(listOf("finished"), loaded.rest.map { it.session.id })
        assertTrue(loaded.waitingForYou.single { it.session.id == "subgroup" }.isSubgroup)
    }

    /**
     * A finished ceremony still owes its coordinator a room, and owes everybody
     * else nothing -- a row telling the rest of the group to create it would be
     * telling them to do something they cannot.
     */
    @Test
    fun `only the coordinator is told to finish setting up a finished ceremony`() = runBlocking {
        val theirs = session("theirs", DkgRitualStage.COMPLETE).copy(coordinatorPublicKey = ada)
        val ours = session("ours", DkgRitualStage.COMPLETE)

        val loaded = load(
            sessions = listOf(theirs, ours),
            roomAwaitingCreation = "theRoomItDerives"
        )

        assertEquals(listOf("ours"), loaded.waitingForYou.map { it.session.id })
        assertEquals("Ada", loaded.rest.single().coordinatorName)
        assertEquals("You", loaded.waitingForYou.single().coordinatorName)
    }

    /**
     * An abandoned ceremony is asked nothing at all. There is no step left to
     * approve and no key to build a room from, so a row that claimed otherwise
     * would send a member to a screen with nothing on it.
     */
    @Test
    fun `an abandoned ceremony is never waiting on anybody`() = runBlocking {
        val loaded = load(
            sessions = listOf(session("abandoned", DkgRitualStage.FAILED)),
            // Both answers say yes, and both have to be ignored on a failure.
            pending = mapOf("abandoned" to DkgApprovalStep.ROUND_1),
            roomAwaitingCreation = "theRoomItDerives"
        )

        assertTrue(loaded.waitingForYou.isEmpty())

        val row = loaded.rest.single()
        assertNull(row.pendingApproval)
        assertNull(row.roomAwaitingCreation)
    }

    private suspend fun load(
        sessions: List<DkgSession>,
        pending: Map<String, DkgApprovalStep> = emptyMap(),
        roomAwaitingCreation: String? = null,
    ): DkgRitualListUIState.Loaded {
        val viewModel = DkgRitualListViewModel(
            chatRoomId = chatRoomId,
            activeUserPublicKey = me,
            initialDkgRitualListUIState = DkgRitualListUIState.Loading,
            chatRepository = object : ChatRepository by ChatRepository.NO_OP_CHAT_REPOSITORY {
                override suspend fun getChatRoomByIdentifier(id: HexKey): LocalChatRoom? = room()
            },
            dkgRepository = object : DkgRepository by DkgRepository.NO_OP_DKG_REPOSITORY {
                override fun observeSessionsForChatRoom(
                    chatRoomId: String
                ): Flow<List<DkgSession>> = flowOf(sessions)

                override suspend fun pendingApproval(session: DkgSession): DkgApprovalStep? =
                    pending[session.id]

                override suspend fun roomAwaitingCreation(session: DkgSession): String? =
                    roomAwaitingCreation
            }
        )

        viewModel.initiate()

        return withTimeout(5_000) {
            viewModel.dkgRitualListUIState.first { it is DkgRitualListUIState.Loaded }
        } as DkgRitualListUIState.Loaded
    }

    private fun room() = LocalChatRoom(
        chatRoom = ChatRoom(
            id = chatRoomId,
            subject = "Ekklesia",
            description = null
        ),
        localParticipants = listOf(me to null, ada to "Ada").map { (publicKey, name) ->
            LocalParticipant(
                participant = Participant(
                    participantPublicKey = publicKey,
                    chatRoomId = chatRoomId,
                    relayHint = null,
                ),
                profile = name?.let {
                    Profile(
                        publicKey = publicKey,
                        displayName = it,
                        nostrEventId = "ee".repeat(32),
                    )
                },
            )
        },
    )

    private fun session(id: String, stage: DkgRitualStage) = DkgSession(
        id = id,
        chatRoomId = chatRoomId,
        coordinatorPublicKey = me,
        userPublicKey = me,
        threshold = 2,
        participantCount = 2,
        hostPublicKey = me,
        round1Random = "66".repeat(32),
        round2AuxRandom = "77".repeat(32),
        stage = stage,
    )
}
