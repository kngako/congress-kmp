package press.mantra.compose.ui.view.model

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import press.mantra.compose.database.model.ChatRoom
import press.mantra.compose.database.model.DkgSession
import press.mantra.compose.database.model.FrostSigningItem
import press.mantra.compose.database.model.FrostSigningSession
import press.mantra.compose.database.model.Participant
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.database.model.intermdiate.LocalFrostSigningSession
import press.mantra.compose.database.model.intermdiate.LocalParticipant
import press.mantra.compose.database.model.types.DkgApprovalStep
import press.mantra.compose.database.model.types.DkgRitualStage
import press.mantra.compose.repository.ChatRepository
import press.mantra.compose.repository.DkgRepository
import press.mantra.compose.ui.view.state.DkgRitualUIState
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * The two ways the key-ceremony screen came to offer to start the ceremony it
 * was already showing.
 *
 * "Start key ceremony" is the whole no-ceremony screen -- the explainer, the
 * quorum picker and the button -- so anything that leaves the state saying
 * there is no ceremony puts that offer in front of a member watching one run.
 * Both of these did, and both are invisible to a test that only asks the view
 * model what it settles on: the state was right before and right after, and
 * wrong in between for long enough to read and to tap.
 *
 * Neither is about ChillDKG. They are about one `DkgRitualUIState` being
 * assembled by seven flows at once, which is why they are tested here rather
 * than against the ritual.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DkgRitualStartOfferJvmTest {
    private val chatRoomId = "a1b2c3d4".repeat(8)
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

    /**
     * The first loaded state already knows the room is running a ceremony.
     *
     * The screen used to publish a bare `Loaded` and then wait for the rows,
     * and a `Loaded` with no session and no ceremony *is* the offer to start
     * one. On a cold open -- the only time the queries are slow -- that is
     * exactly when it was shown.
     */
    @Test
    fun `the offer to start is not published while the rows are still being read`() = runBlocking {
        val session = session(DkgRitualStage.COLLECTING_HOST_KEYS)

        val viewModel = viewModel(
            object : DkgRepository by DkgRepository.NO_OP_DKG_REPOSITORY {
                // Slow on purpose: a Room query answers in microseconds and
                // would hide the window rather than prove it is gone.
                override fun observeLatestSessionForChatRoom(
                    chatRoomId: String,
                    parentChatRoomId: String?,
                ): Flow<DkgSession?> = flow {
                    delay(50)
                    emit(session)
                }
            }
        )

        viewModel.initiate()

        val firstLoaded = withTimeout(5_000) {
            viewModel.dkgRitualUIState.first { it is DkgRitualUIState.Loaded }
        } as DkgRitualUIState.Loaded

        assertEquals(session.id, firstLoaded.session?.id, "the first loaded state missed the ceremony")
        assertFalse(
            viewModel.canStartRitual(firstLoaded),
            "offered to start a ceremony on a room already running one"
        )
    }

    /**
     * A write that suspends between reading the state and writing it back does
     * not carry the stale copy over somebody else's change.
     *
     * Every flow folding into this state used to read it, build a `copy` and
     * assign -- and the session watcher asks the database what this member owes
     * in the middle of that. The key-state watcher writing during that pause had
     * its change thrown away by the assignment that followed. The same pause in
     * the other direction is what put `session` back to null and offered to
     * start the ceremony on screen; this is the half of it that can be forced to
     * happen rather than raced into.
     */
    @Test
    fun `a change written during a suspension survives the write that follows it`() = runBlocking {
        val session = session(DkgRitualStage.COLLECTING_ROUND_1)
        val signingSessions = MutableStateFlow<List<LocalFrostSigningSession>>(emptyList())

        // Held open until the test has had the key-state watcher write. The
        // seeding call runs before anything else is collecting, so it is let
        // through; the session watcher's call is the one that waits.
        val gate = CompletableDeferred<Unit>()
        var calls = 0

        val viewModel = viewModel(
            object : DkgRepository by DkgRepository.NO_OP_DKG_REPOSITORY {
                override fun observeLatestSessionForChatRoom(
                    chatRoomId: String,
                    parentChatRoomId: String?,
                ): Flow<DkgSession?> = flowOf(session)

                override fun observeSigningSessions(
                    chatRoomId: String
                ): Flow<List<LocalFrostSigningSession>> = signingSessions

                override suspend fun pendingApproval(session: DkgSession): DkgApprovalStep? {
                    calls += 1
                    if (calls == 1) return null

                    gate.await()
                    return DkgApprovalStep.ROUND_1
                }
            }
        )

        viewModel.initiate()

        withTimeout(5_000) { viewModel.dkgRitualUIState.first { it is DkgRitualUIState.Loaded } }

        // The watcher is now parked inside `pendingApproval`, holding a copy of
        // the state as it was before this line.
        signingSessions.value = listOf(keyStateSigningSession())

        withTimeout(5_000) {
            viewModel.dkgRitualUIState.first {
                (it as? DkgRitualUIState.Loaded)?.keyStateSession != null
            }
        }

        gate.complete(Unit)

        val settled = withTimeout(5_000) {
            viewModel.dkgRitualUIState.first {
                (it as? DkgRitualUIState.Loaded)?.pendingApproval == DkgApprovalStep.ROUND_1
            }
        } as DkgRitualUIState.Loaded

        assertNotNull(
            settled.keyStateSession,
            "the session watcher wrote back a copy taken before the key state arrived"
        )
        assertEquals(session.id, settled.session?.id)
    }

    private fun viewModel(dkgRepository: DkgRepository) = DkgRitualViewModel(
        chatRoomId = chatRoomId,
        activeUserPublicKey = me,
        initialDkgRitualUIState = DkgRitualUIState.Loading,
        activeIdentityStateFlow = MutableStateFlow(null),
        chatRepository = object : ChatRepository by ChatRepository.NO_OP_CHAT_REPOSITORY {
            override suspend fun getChatRoomByIdentifier(id: HexKey): LocalChatRoom? = room()
        },
        dkgRepository = dkgRepository
    )

    private fun room() = LocalChatRoom(
        chatRoom = ChatRoom(
            id = chatRoomId,
            subject = "Ekklesia",
            description = null
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

    private fun session(stage: DkgRitualStage) = DkgSession(
        id = "ceremony",
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

    private fun keyStateSigningSession() = LocalFrostSigningSession(
        session = FrostSigningSession(
            id = "signing",
            chatRoomId = chatRoomId,
            coordinatorPublicKey = me,
            userPublicKey = me,
            dkgSessionId = "ceremony",
            threshold = 2,
            participantCount = 2,
            signerId = 0,
        ),
        unorderedItems = listOf(
            FrostSigningItem(
                sessionId = "signing",
                itemIndex = 0,
                unsignedEventJson = """{"id":"${"33".repeat(32)}","pubkey":"$me",""" +
                    """"created_at":0,"kind":30326,"tags":[],"content":"","sig":"${"44".repeat(64)}"}""",
                eventId = "33".repeat(32),
                nonceRandom = "55".repeat(32),
            )
        ),
    )
}
