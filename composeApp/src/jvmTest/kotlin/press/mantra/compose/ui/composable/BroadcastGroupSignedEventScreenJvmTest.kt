package press.mantra.compose.ui.composable

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import press.mantra.compose.database.model.ChatRoom
import press.mantra.compose.database.model.GroupSignedEvent
import press.mantra.compose.database.model.NostrEvent
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.exceptions.NostrPublishException
import press.mantra.compose.network.dto.RelayDTO
import press.mantra.compose.network.relays.EventPublishTransport
import press.mantra.compose.network.relays.NostrPublishResult
import press.mantra.compose.network.sockets.NostrIncomingMessage
import press.mantra.compose.nostr.Relays
import press.mantra.compose.repository.ChatRepository
import press.mantra.compose.ui.composable.widgets.ProvideSnackbarHost
import press.mantra.compose.ui.theme.MantraTheme
import press.mantra.compose.ui.view.state.BroadcastGroupSignedEventUIState
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * What the broadcast screen shows before, and after, the button.
 *
 * Before: the event named as the signing screen would name it, the app's own
 * publish relays already in the list, and a way to add one. After: each relay's
 * answer under its own row, in the relay's words where it gave any, and the sum
 * above them -- because the sum is what the member came for and the words are
 * what they can act on.
 */
@OptIn(ExperimentalTestApi::class)
class BroadcastGroupSignedEventScreenJvmTest {

    private val chatRoomId = "d4c3b2a1".repeat(8)

    /** The first two relays the screen seeds, in the order it lists them. */
    private val seeded = Relays.eventPublishRelaySet.toList()
    private val one = seeded[0]
    private val two = seeded[1]

    /** A pool that answers as told and never opens a socket. */
    private class FakeTransport(
        private val answers: List<NostrPublishResult>
    ) : EventPublishTransport {
        override suspend fun publishEvent(
            nostrEvent: NostrEvent,
            relays: Set<RelayDTO>
        ): Flow<NostrPublishResult> = flowOf(*answers.toTypedArray())
    }

    @Test
    fun `the event is named, the app's relays are filled in, and the JSON is there to copy`() =
        render(state()) {
            // A plain note, which the signing screen has no words of its own
            // for, so it is named by its number over its content.
            onNodeWithText("Event of kind 1").assertIsDisplayed()
            onNodeWithText("The first chapter is out.").assertIsDisplayed()

            // The app's own publish set, as hosts.
            seeded.forEach { relay ->
                onNodeWithText(relay.displayUrl()).assertIsDisplayed()
            }

            onNodeWithText("Broadcast").assertIsDisplayed()
            onNodeWithText("Copy raw JSON").assertIsDisplayed()

            // The relays come before the JSON: the list is the screen's job and
            // the JSON is for whoever would rather use a different tool.
            val relays = onNodeWithText(one.displayUrl()).getBoundsInRoot().top
            val json = onNodeWithText("The signed event").getBoundsInRoot().top
            assertTrue(relays < json, "the JSON climbed above the relay list")
        }

    @Test
    fun `a relay can be added, and a bad one is refused by name`() = render(state()) {
        onNodeWithText("Relay url").performTextInput("wss://relay.three.example")
        onNodeWithContentDescription("Add relay").performClick()
        onNodeWithText("relay.three.example").assertIsDisplayed()

        onNodeWithText("Relay url").performTextInput("not a relay")
        onNodeWithContentDescription("Add relay").performClick()
        onNodeWithText("That isn't a relay address.", substring = true).assertIsDisplayed()
    }

    @Test
    fun `after the button, each relay says what it answered and the sum sits above them`() =
        render(
            uiState = state(),
            transport = FakeTransport(
                listOf(
                    NostrPublishResult(
                        result = NostrIncomingMessage.OkMessage(eventId = "b".repeat(64), success = true),
                        relayUrl = one.url,
                    ),
                    NostrPublishResult(
                        result = NostrIncomingMessage.OkMessage(
                            eventId = "b".repeat(64),
                            success = false,
                            message = "blocked: not on the allow list",
                        ),
                        error = NostrPublishException(message = "blocked: not on the allow list"),
                        relayUrl = two.url,
                    ),
                )
            )
        ) {
            onNodeWithContentDescription("Broadcast to these relays").performClick()

            // The two that answered are counted; the rest of the seeded list
            // never did, and the sum says so by counting them as asked.
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithText("Accepted by 1 of ${seeded.size} relays.")
                    .fetchSemanticsNodes().isNotEmpty()
            }

            onNodeWithText("Accepted").assertIsDisplayed()
            onNodeWithText("Rejected: blocked: not on the allow list").assertIsDisplayed()

            // The answer under the relay it came from, not in a list of its own.
            val relayOne = onNodeWithText(one.displayUrl()).getBoundsInRoot().top
            val accepted = onNodeWithText("Accepted").getBoundsInRoot().top
            val relayTwo = onNodeWithText(two.displayUrl()).getBoundsInRoot().top
            val rejected = onNodeWithText("Rejected: blocked: not on the allow list").getBoundsInRoot().top
            assertTrue(relayOne < accepted && accepted < relayTwo, "the OK is not under its relay")
            assertTrue(relayTwo < rejected, "the rejection is not under its relay")
        }

    @Test
    fun `an empty list asks nobody and says to add one`() = render(state()) {
        // Emptied by hand, since the seed always has something in it.
        repeat(seeded.size) {
            onAllNodesWithContentDescription("Remove relay")[0].performClick()
        }

        onNodeWithText("No relays to broadcast to. Add one above.").assertIsDisplayed()
    }

    private fun signedEvent() = GroupSignedEvent(
        id = "b".repeat(64),
        chatRoomId = chatRoomId,
        publicKey = chatRoomId,
        kind = TextNoteEvent.KIND,
        tags = emptyArray(),
        content = "The first chapter is out.",
        signature = "f".repeat(128),
        createdAt = Instant.fromEpochSeconds(1_700_000_000),
    )

    private fun state() = BroadcastGroupSignedEventUIState.Loaded(
        localChatRoom = LocalChatRoom(
            chatRoom = ChatRoom(
                id = chatRoomId,
                subject = "Translation room",
                description = "A group translating hard books."
            )
        ),
        signedEvent = signedEvent(),
    )

    private fun render(
        uiState: BroadcastGroupSignedEventUIState,
        transport: EventPublishTransport = FakeTransport(emptyList()),
        assertions: ComposeUiTest.() -> Unit
    ) = runDesktopComposeUiTest(width = 400, height = 2400) {
        setContent {
            MantraTheme {
                ProvideSnackbarHost {
                    Box(modifier = Modifier.fillMaxSize()) {
                        BroadcastGroupSignedEventScreen(
                            activeUserPublicKey = "a".repeat(64),
                            chatRoomId = chatRoomId,
                            relayHint = null,
                            signedEventId = "b".repeat(64),
                            initialBroadcastGroupSignedEventUIState = uiState,
                            chatRepository = ChatRepository.NO_OP_CHAT_REPOSITORY,
                            publishTransport = transport,
                            onNavigateBack = {}
                        )
                    }
                }
            }
        }

        assertions()
    }
}
