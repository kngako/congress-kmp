package press.mantra.compose.ui.view.model

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import press.mantra.compose.database.model.ChatRoom
import press.mantra.compose.database.model.DkgSession
import press.mantra.compose.database.model.NegentropySynchronizeRequest
import press.mantra.compose.database.model.Participant
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.database.model.intermdiate.LocalParticipant
import press.mantra.compose.database.model.types.DkgApprovalStep
import press.mantra.compose.database.model.types.DkgRitualStage
import press.mantra.compose.repository.ChatRepository
import press.mantra.compose.repository.DkgRepository
import press.mantra.compose.repository.FrostSigningRepository
import press.mantra.compose.repository.NostrRepository
import press.mantra.compose.ui.view.state.ChatMessageListUIState
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * When tapping one ceremony in the transcript is answering the whole question,
 * and when it is answering a third of it.
 *
 * The transcript names one ceremony per line and its standing notice stands for
 * however many are owed. Opening the named one is right while it is the only
 * thing being asked; it stops being right the moment there are two, because the
 * second is not on screen beside the first -- it may be pages up, or have
 * arrived while the member was reading -- so answering the one they tapped and
 * leaving would look exactly like being done.
 *
 * `hidesOtherDecisions` has made this call for proposals since before subgroups
 * existed. [ChatMessageListViewModel.hidesOtherCeremonies] is its counterpart,
 * and a room holds several ceremonies as a matter of course now that a
 * subgroup's ChillDKG runs in its parent's room.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CeremonyQueueRoutingJvmTest {
    private val chatRoomId = "aa".repeat(32)
    private val me = "11".repeat(32)
    private val peer = "22".repeat(32)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `one ceremony owed is answered by opening it`() = runBlocking {
        val viewModel = awaiting(
            sessions = listOf(session("only", DkgRitualStage.COLLECTING_ROUND_1)),
            pending = mapOf("only" to DkgApprovalStep.ROUND_1),
            expected = 1
        )

        assertFalse(
            viewModel.hidesOtherCeremonies("only"),
            "the one ceremony owed was sent to a queue of itself"
        )
    }

    @Test
    fun `two ceremonies owed send the member to the list instead`() = runBlocking {
        val viewModel = awaiting(
            sessions = listOf(
                session("ours", DkgRitualStage.COLLECTING_ROUND_1),
                session("subgroup", DkgRitualStage.COLLECTING_HOST_KEYS)
                    .copy(parentChatRoomId = chatRoomId, subject = "Translators"),
            ),
            pending = mapOf(
                "ours" to DkgApprovalStep.ROUND_1,
                "subgroup" to DkgApprovalStep.HOST_KEY
            ),
            expected = 2
        )

        // Either tap: the queue is the honest answer to both, because neither is
        // the whole of what the room is waiting on this member for.
        assertTrue(viewModel.hidesOtherCeremonies("ours"))
        assertTrue(viewModel.hidesOtherCeremonies("subgroup"))
    }

    /**
     * The restraint, and the reason this is not just "does the room owe two".
     * A line about a ceremony the group finished is history, and a reader who
     * taps one is asking to see that -- not to be handed a queue of other
     * people's business.
     */
    @Test
    fun `a finished ceremony is opened on its own even while others are owed`() = runBlocking {
        val viewModel = awaiting(
            sessions = listOf(
                session("done", DkgRitualStage.COMPLETE),
                session("ours", DkgRitualStage.COLLECTING_ROUND_1),
                session("subgroup", DkgRitualStage.COLLECTING_HOST_KEYS),
            ),
            pending = mapOf(
                "ours" to DkgApprovalStep.ROUND_1,
                "subgroup" to DkgApprovalStep.HOST_KEY
            ),
            expected = 2
        )

        assertFalse(
            viewModel.hidesOtherCeremonies("done"),
            "a finished ceremony's line was answered with somebody else's queue"
        )
    }

    /**
     * An abandoned one is not owed either, which matters because the room's
     * watcher filters on it and a row that slipped through would put a dead
     * ceremony at the top of the notice.
     */
    @Test
    fun `an abandoned ceremony is never counted as owed`() = runBlocking {
        val viewModel = awaiting(
            sessions = listOf(session("abandoned", DkgRitualStage.FAILED)),
            // Says yes, and has to be ignored on a failure.
            pending = mapOf("abandoned" to DkgApprovalStep.ROUND_1),
            expected = 0
        )

        assertTrue(viewModel.ceremoniesAwaitingYou.isEmpty())
        assertFalse(viewModel.hidesOtherCeremonies("abandoned"))
    }

    /** Builds the view model and waits until its watcher has settled on [expected]. */
    private suspend fun awaiting(
        sessions: List<DkgSession>,
        pending: Map<String, DkgApprovalStep>,
        expected: Int,
    ): ChatMessageListViewModel {
        val viewModel = ChatMessageListViewModel(
            initialChatMessageListUIState = ChatMessageListUIState.Loading,
            localChatRoom = room(),
            // Not the bare no-op. `initiate` calls `scheduleSynchronization` on
            // its second line, which launches on `Dispatchers.IO` and -- because
            // `room()` gives every participant a null profile, so there is always
            // somebody it cannot name -- reaches
            // `queueNegentropySynchronizeRequest`. That member is one of the
            // forty-one `TODO("Not yet implemented")` bodies on
            // NO_OP_NOSTR_REPOSITORY, so it throws in a coroutine nobody awaits.
            //
            // Nothing catches it, so it reaches the default handler, and the next
            // `runTest` in the same JVM reports it as `UncaughtExceptionsBeforeTest`
            // -- against its own name. That is how this test failed
            // `NavigationRoutingTest` four times a run while passing itself.
            nostrRepository = object : NostrRepository by NostrRepository.NO_OP_NOSTR_REPOSITORY {
                override suspend fun queueNegentropySynchronizeRequest(
                    negentropySynchronizeRequests: List<NegentropySynchronizeRequest>
                ) = Unit
            },
            chatRepository = ChatRepository.NO_OP_CHAT_REPOSITORY,
            frostSigningRepository = FrostSigningRepository.NO_OP_FROST_SIGNING_REPOSITORY,
            dkgRepository = object : DkgRepository by DkgRepository.NO_OP_DKG_REPOSITORY {
                override fun observeSessionsForChatRoom(
                    chatRoomId: String
                ): Flow<List<DkgSession>> = flowOf(sessions)

                override suspend fun pendingApproval(session: DkgSession): DkgApprovalStep? =
                    pending[session.id]
            }
        )

        viewModel.initiate()

        // The watcher writes once, so an empty expectation cannot be waited for
        // by its own value -- it is indistinguishable from not having run. Wait
        // for a non-empty one, and give an empty one a moment to be wrong in.
        withTimeout(5_000) {
            if (expected == 0) {
                delay(200)
            } else {
                while (viewModel.ceremoniesAwaitingYou.size != expected) delay(5)
            }
        }

        assertEquals(expected, viewModel.ceremoniesAwaitingYou.size)
        return viewModel
    }

    private fun room() = LocalChatRoom(
        chatRoom = ChatRoom(
            id = chatRoomId,
            userPublicKey = me,
            subject = "Ekklesia",
            description = null,
            mlsGroupState = null,
        ),
        localParticipants = listOf(me, peer).map { publicKey ->
            LocalParticipant(
                participant = Participant(
                    participantPublicKey = publicKey,
                    chatRoomId = chatRoomId,
                    relayHint = null,
                ),
                profile = null,
            )
        },
    )

    private fun session(id: String, stage: DkgRitualStage) = DkgSession(
        id = id,
        chatRoomId = chatRoomId,
        coordinatorPublicKey = peer,
        userPublicKey = me,
        threshold = 2,
        participantCount = 2,
        hostPublicKey = peer,
        round1Random = "66".repeat(32),
        round2AuxRandom = "77".repeat(32),
        stage = stage,
    )
}
