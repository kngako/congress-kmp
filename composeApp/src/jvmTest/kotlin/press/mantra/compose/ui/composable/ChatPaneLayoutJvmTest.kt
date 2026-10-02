package press.mantra.compose.ui.composable

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import press.mantra.compose.database.model.ChatRoom
import press.mantra.compose.database.model.NostrEvent
import press.mantra.compose.database.model.Profile
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.database.model.intermdiate.LocalProfileWithFollowing
import press.mantra.compose.repository.ChatRepository
import press.mantra.compose.repository.DkgRepository
import press.mantra.compose.repository.FrostSigningRepository
import press.mantra.compose.repository.NostrRepository
import press.mantra.compose.ui.composable.widgets.ProvideSnackbarHost
import press.mantra.compose.ui.theme.MantraTheme
import press.mantra.compose.ui.view.state.HomeScreenUIState
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import androidx.compose.ui.test.ComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The chat list-detail split, measured in windows of the widths the phase names.
 *
 * Five claims, and none of them can be read off the source:
 *
 *  - below expanded the home screen is unchanged. That is the promise that let the split
 *    land as one commit -- the chat room is a navigation destination reached from eleven
 *    places, and on a phone it stays one;
 *  - at expanded and above the list pane is exactly the width
 *    `calculatePaneScaffoldDirective` gives it, so a hand-built pair measures the same as
 *    a `ListDetailPaneScaffold` would;
 *  - the detail pane says what it is for while nothing is selected, rather than being an
 *    unexplained empty half of the window;
 *  - a second tap in the list changes what the detail pane shows. That one is not a
 *    layout claim at all -- it is about view model scope -- but it is only reachable
 *    through this layout, and it is the way the split visibly failed on a tablet;
 *  - and the list says which room the pane is answering with, in the one layout where
 *    that question exists.
 *
 * The repositories are the no-op ones with the two reads this screen makes delegated to a
 * fixed answer. Kotlin's interface delegation makes that ten lines rather than a
 * reimplementation of two large interfaces, and it keeps the test about layout: the screen
 * asks for a profile and a room list, and what matters here is where they are drawn.
 */
@OptIn(ExperimentalTestApi::class)
class ChatPaneLayoutJvmTest {

    private val publicKey = "de1a1e64d1c4e0d6bd97b0e73d4dfd0e1ec1cdd1e6d8d0e2b9a7f3c5d0e1a2b3"
    private val emptyDetail = "Pick a conversation to read it here."
    private val roomOne = "Room one"
    private val roomTwo = "Room two"

    /**
     * Opening a room is a database read on another dispatcher, so the assertions wait
     * rather than assume. Generous, because what it is waiting for is a thread hop and the
     * cost of being wrong about it is a test that fails on a loaded machine.
     */
    private val OpenTimeout = 5_000L

    @Test
    fun `a phone window is one pane, as it has always been`() = runDesktopComposeUiTest(400, 900) {
        setContent { Home() }

        onNodeWithText(emptyDetail).assertDoesNotExist()
    }

    @Test
    fun `a medium window is still one pane, because a transcript is dense`() =
        runDesktopComposeUiTest(700, 900) {
            setContent { Home() }

            // M3: no two panes in a medium window with high information density.
            onNodeWithText(emptyDetail).assertDoesNotExist()
        }

    @Test
    fun `an expanded window splits, with the directive's 360dp list pane`() =
        runDesktopComposeUiTest(1000, 900) {
            setContent { Home() }

            onNodeWithText(emptyDetail).assertIsDisplayed()
            onNodeWithTag(ChatListPaneTag).assertWidthIsEqualTo(360.dp)
        }

    @Test
    fun `a large window widens the list pane to 412dp`() = runDesktopComposeUiTest(1400, 900) {
        setContent { Home() }

        onNodeWithText(emptyDetail).assertIsDisplayed()
        onNodeWithTag(ChatListPaneTag).assertWidthIsEqualTo(412.dp)
    }

    @Test
    fun `tapping a second room in the list changes what the detail pane shows`() =
        runDesktopComposeUiTest(1000, 900) {
            setContent { Home(chatRepository = TwoRooms) }

            // Each room's subject is drawn twice once it is the selection -- on its card in
            // the list, and as the title of the detail pane's app bar -- so two nodes is
            // "this is the room on the right" and one is "this room is merely listed".
            onNodeWithText(roomOne).performClick()
            waitUntil("room one opens", OpenTimeout) { nodesSaying(roomOne) == 2 }

            onNodeWithText(roomTwo).performClick()

            // Before the pane scoped its view models this timed out: `key(chatRoomId)` was
            // rebuilding the composition around a `ChatRoomMessagingViewModel` that
            // `viewModel()` kept resolving, by class, out of the home route's store -- so
            // the pane answered with room one however many rooms were tapped after it.
            waitUntil("room two replaces it", OpenTimeout) {
                nodesSaying(roomTwo) == 2 && nodesSaying(roomOne) == 1
            }
            assertEquals(2, nodesSaying(roomTwo), "room two is listed and shown")
            assertEquals(1, nodesSaying(roomOne), "room one is listed, not shown")
        }

    @Test
    fun `the list marks the room the detail pane is showing`() =
        runDesktopComposeUiTest(1000, 900) {
            setContent { Home(chatRepository = TwoRooms) }

            // Selectable, and nothing selected. A pane that has been asked nothing yet is
            // a different state from a room that is not the one being shown, and the list
            // is allowed to say so. `card` only matches a selectable node, so finding both
            // rooms through it is half of the claim.
            card(roomOne).assertIsNotSelected()
            card(roomTwo).assertIsNotSelected()

            card(roomOne).performClick()
            waitUntil("room one opens", OpenTimeout) { nodesSaying(roomOne) == 2 }
            card(roomOne).assertIsSelected()
            card(roomTwo).assertIsNotSelected()

            card(roomTwo).performClick()
            waitUntil("room two opens", OpenTimeout) { nodesSaying(roomTwo) == 2 }
            card(roomTwo).assertIsSelected()

            // The mark moves rather than accumulating. Two highlighted rows beside one
            // transcript would say the pane was showing both.
            card(roomOne).assertIsNotSelected()
        }

    @Test
    fun `a one-pane list says nothing about selection, having nothing to select into`() =
        runDesktopComposeUiTest(700, 900) {
            setContent { Home(chatRepository = TwoRooms) }

            // Tapping navigates here, and the test's callback is a no-op, so the list is
            // still on screen afterwards -- which is the only reason this can be asked at
            // all. A row answering "not selected" would be describing a pane that is not
            // there, and a highlight left behind would outlive the screen it belonged to.
            onNodeWithText(roomOne).performClick()

            // Both halves, because either alone passes for the wrong reason: a list that
            // never rendered has no selectable rows either.
            onNodeWithText(roomOne).assertExists()
            card(roomOne).assertDoesNotExist()
        }

    @androidx.compose.runtime.Composable
    private fun Home(chatRepository: ChatRepository = NoRooms) {
        MantraTheme {
            // Required, and deliberately so: `LocalSnackbarHostState` throws rather than
            // defaulting, because a default would make every `notify` on a screen that
            // forgot the host a silent no-op.
            ProvideSnackbarHost {
            HomeScreen(
                activeUserPublicKey = publicKey,
                initialHomeScreenUIState = HomeScreenUIState.Loaded(profileWithFollowing = profile),
                onNavigateToRoute = {},
                onNavigateToDirectMessageDetail = {},
                onNavigateToChatRoomCreation = {},
                nostrRepository = FixedProfile,
                chatRepository = chatRepository,
                frostSigningRepository = FrostSigningRepository.NO_OP_FROST_SIGNING_REPOSITORY,
                dkgRepository = DkgRepository.NO_OP_DKG_REPOSITORY,
            )
            }
        }
    }

    private val profile = LocalProfileWithFollowing(
        nostrEvent = NostrEvent(
            id = "aa11bb22cc33dd44ee55ff6600778899aabbccddeeff00112233445566778899",
            pubKey = publicKey,
            content = "",
            tags = emptyArray(),
            sig = "",
            kind = TextNoteEvent.KIND,
        ),
        profile = Profile(
            publicKey = publicKey,
            displayName = "Reader",
            nostrEventId = "aa11bb22cc33dd44ee55ff6600778899aabbccddeeff00112233445566778899",
        ),
        following = emptyList(),
    )

    /** The no-op repository, plus the one read the home screen makes of it. */
    private val FixedProfile = object : NostrRepository by NostrRepository.NO_OP_NOSTR_REPOSITORY {
        override suspend fun observeProfileWithFollowing(
            publicKey: String,
        ): Flow<LocalProfileWithFollowing?> = flowOf(profile)
    }

    /** The no-op repository, plus the one read the room list makes of it. */
    private val NoRooms = rooms()

    /** Two rooms, told apart by their subject, which is what both panes draw as a title. */
    private val TwoRooms = rooms(room("1", roomOne), room("2", roomTwo))

    /**
     * The no-op repository, plus the two reads this pair of panes makes of it: the list
     * observes every room, and the detail pane looks one up by id.
     */
    private fun rooms(vararg rooms: LocalChatRoom) =
        object : ChatRepository by ChatRepository.NO_OP_CHAT_REPOSITORY {
            override suspend fun observeChatRoomListByPublicKey(
                publicKey: String,
            ): Flow<List<LocalChatRoom>> = flowOf(rooms.toList())

            override suspend fun getChatRoomByIdentifier(id: HexKey): LocalChatRoom? =
                rooms.firstOrNull { it.chatRoom.id == id }
        }

    private fun room(id: String, subject: String) = LocalChatRoom(
        chatRoom = ChatRoom(
            id = id,
            subject = subject,
            description = null
        ),
    )

    private fun ComposeUiTest.nodesSaying(text: String) =
        onAllNodesWithText(text).fetchSemanticsNodes().size

    /**
     * The list's card for the room with this subject.
     *
     * `Card(onClick = ...)` merges its descendants, so one node carries both the subject
     * and the selected state -- which is also why a selected room is two nodes saying its
     * subject and a merely listed one is a single node. The detail pane's app bar says the
     * same subject and is not selectable, so this stays unambiguous once a room is open.
     */
    private fun ComposeUiTest.card(subject: String) =
        onNode(isSelectable() and hasText(subject))
}
