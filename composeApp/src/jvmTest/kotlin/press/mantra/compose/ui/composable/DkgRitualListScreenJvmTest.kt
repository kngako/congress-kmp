package press.mantra.compose.ui.composable

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import press.mantra.compose.database.model.ChatRoom
import press.mantra.compose.database.model.DkgSession
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.database.model.types.DkgApprovalStep
import press.mantra.compose.database.model.types.DkgRitualStage
import press.mantra.compose.repository.ChatRepository
import press.mantra.compose.repository.DkgRepository
import press.mantra.compose.ui.composable.navigation.routes.DkgRitualRoute
import press.mantra.compose.ui.composable.navigation.routes.Route
import press.mantra.compose.ui.composable.widgets.ProvideSnackbarHost
import press.mantra.compose.ui.theme.MantraTheme
import press.mantra.compose.ui.view.state.DkgRitualListUIState
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What the ceremony list is for, read off the rendered screen.
 *
 * The ordering is settled in [press.mantra.compose.ui.view.model.DkgRitualListViewModel]
 * and tested there. What is only true once it is drawn is that a row goes to
 * the ceremony it names -- the whole reason this screen exists is that the
 * screens before it could not say which of a room's ceremonies they meant --
 * and that a room with none offers the way to hold one rather than ending in a
 * blank page.
 */
@OptIn(ExperimentalTestApi::class)
class DkgRitualListScreenJvmTest {
    private val me = "11".repeat(32)
    private val chatRoomId = "22".repeat(32)

    @Test
    fun `a row opens the ceremony it names, not the room's newest`() =
        runDesktopComposeUiTest(500, 1200) {
            var opened: Route? = null

            setContent {
                Screen(
                    state = loaded(
                        ceremony("subgroup", DkgRitualStage.COLLECTING_HOST_KEYS).copy(
                            pendingApproval = DkgApprovalStep.HOST_KEY
                        ),
                        ceremony("newest", DkgRitualStage.COMPLETE)
                    ),
                    onNavigateToRoute = { opened = it }
                )
            }

            onNodeWithText("Join it by publishing your device's key")
                .assertIsDisplayed()
                .performClick()

            assertEquals(
                DkgRitualRoute(
                    activeUserPublicKey = me,
                    chatRoomId = chatRoomId,
                    dkgSessionId = "subgroup"
                ),
                opened
            )
        }

    /** The two sections, so a ceremony owing nothing cannot be mistaken for one that does. */
    @Test
    fun `the ones waiting on you are headed apart from the rest`() =
        runDesktopComposeUiTest(500, 1200) {
            setContent {
                Screen(
                    state = loaded(
                        ceremony("subgroup", DkgRitualStage.COLLECTING_HOST_KEYS).copy(
                            pendingApproval = DkgApprovalStep.HOST_KEY
                        ),
                        ceremony("newest", DkgRitualStage.COMPLETE)
                    )
                )
            }

            onNodeWithText("Waiting for you").assertIsDisplayed()
            onNodeWithText("Everything else").assertIsDisplayed()
            onNodeWithText("The group has a key from this ceremony.").assertIsDisplayed()
        }

    @Test
    fun `a room that has held none offers to hold one`() =
        runDesktopComposeUiTest(500, 1200) {
            var opened: Route? = null

            setContent { Screen(state = loaded(), onNavigateToRoute = { opened = it }) }

            onNodeWithText("This group has not held a key ceremony.").assertIsDisplayed()
            onNodeWithText("Set up a shared key").assertIsDisplayed().performClick()

            // No session id: there is no ceremony to name, and the ceremony
            // screen is what decides whether the group can hold one at all.
            assertEquals(
                DkgRitualRoute(activeUserPublicKey = me, chatRoomId = chatRoomId),
                opened
            )
        }

    @Composable
    private fun Screen(
        state: DkgRitualListUIState,
        onNavigateToRoute: (Route) -> Unit = {},
    ) {
        MantraTheme {
            ProvideSnackbarHost {
                DkgRitualListScreen(
                    activeUserPublicKey = me,
                    chatRoomId = chatRoomId,
                    initialDkgRitualListUIState = state,
                    chatRepository = ChatRepository.NO_OP_CHAT_REPOSITORY,
                    dkgRepository = DkgRepository.NO_OP_DKG_REPOSITORY,
                    onNavigateBack = {},
                    onNavigateToRoute = onNavigateToRoute,
                )
            }
        }
    }

    private fun loaded(vararg ceremonies: DkgRitualListUIState.Ceremony) =
        DkgRitualListUIState.Loaded(
            localChatRoom = LocalChatRoom(
                chatRoom = ChatRoom(
                    id = chatRoomId,
                    subject = "Ekklesia",
                    description = null
                )
            ),
            ceremonies = ceremonies.toList()
        )

    private fun ceremony(id: String, stage: DkgRitualStage) = DkgRitualListUIState.Ceremony(
        session = DkgSession(
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
        ),
        coordinatorName = "You",
    )
}
