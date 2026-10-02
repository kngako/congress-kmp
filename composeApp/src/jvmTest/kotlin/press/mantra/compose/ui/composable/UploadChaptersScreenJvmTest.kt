package press.mantra.compose.ui.composable

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.Test
import press.mantra.compose.database.model.ChatRoom
import press.mantra.compose.database.model.MantraArtifact
import press.mantra.compose.database.model.MantraArtifactVersion
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.repository.ChatRepository
import press.mantra.compose.repository.FrostSigningRepository
import press.mantra.compose.repository.MantraRepository
import press.mantra.compose.ui.composable.widgets.ProvideSnackbarHost
import press.mantra.compose.ui.theme.MantraTheme
import press.mantra.compose.ui.view.state.PickedChapter
import press.mantra.compose.ui.view.state.UploadChaptersUIState
import press.mantra.compose.ui.view.state.UploadProblem

/**
 * What the upload screen says before anything has been proposed.
 *
 * The screen is a review step, so its whole job is to be read: the cost of the
 * upload in front of the picker, a row per file saying where it lands and what
 * is wrong with it, and a button that offers only what can actually be
 * proposed. None of that is checkable from the view model -- `canPropose` is
 * read by the FAB and the per-row reasons are rendered from a `when` -- so it is
 * checked here, against a real composition.
 */
@OptIn(ExperimentalTestApi::class)
class UploadChaptersScreenJvmTest {

    private val room = LocalChatRoom(
        chatRoom = ChatRoom(
            id = "room",
            subject = "#Ekklesia",
            description = ""
        )
    )

    private val artifact = MantraArtifact(
        id = "artifactId",
        publicKey = "author",
        name = "To Kill a Mockingbird",
        url = "https://example.test/bird",
        visibility = "private",
        dialectId = "dialectId",
        license = "cc",
        chatRoomId = "room",
        signature = "",
    )

    private val version = MantraArtifactVersion(
        id = "artifactVersionId",
        artifactId = "artifactId",
        publicKey = "author",
        versionLabel = "1.0",
        chatRoomId = "room",
        signature = "",
    )

    private fun row(
        id: String,
        name: String = id,
        problem: UploadProblem? = null,
        sessionId: String? = null,
    ) = PickedChapter(
        id = id,
        fileName = "$id.md",
        name = name,
        markdown = "A paragraph.",
        chunks = listOf("A paragraph."),
        wordCount = 2,
        characterCount = 12,
        proposalBytes = 1000,
        problem = problem,
        sessionId = sessionId,
    )

    @Test
    fun `before any file is picked, the cost of an upload is already on screen`() = render(
        state(chapters = emptyList())
    ) {
        // Said before the picker opens, not after: an upload spends the room's
        // attention and the person picking files is the only one who can weigh
        // it.
        onNodeWithText("Every file becomes its own proposal", substring = true).assertIsDisplayed()
        onNodeWithText("At most 20 files in one upload.").assertIsDisplayed()
        onNodeWithText("No files picked yet.").assertIsDisplayed()
        onNodeWithText("Choose files").assertIsDisplayed()
    }

    @Test
    fun `a picked file shows where it lands and what it will be called`() = render(
        state(chapters = listOf(row("the-wreck", name = "The wreck")), baseIndex = 3)
    ) {
        onNodeWithText("the-wreck.md").assertIsDisplayed()
        // Numbered from the version's own next index, not from one.
        onNodeWithText("Chapter 3 of the version").assertIsDisplayed()
        onNodeWithText("The wreck").assertIsDisplayed()
    }

    @Test
    fun `the button counts what it would actually propose`() = render(
        state(
            chapters = listOf(
                row("good"),
                row("binary", problem = UploadProblem.NotText),
                row("also-good"),
            )
        )
    ) {
        // Two, not three: a file that is not text was never offered.
        onNodeWithText("Propose 2 chapters").assertIsEnabled()
        onNodeWithText("Not text. A chapter is markdown or plain text.").assertIsDisplayed()
    }

    @Test
    fun `a group with no shared key is told, and offered nothing`() = render(
        state(canSign = false)
    ) {
        onNodeWithText("This group has no shared key", substring = true).assertIsDisplayed()
        onNodeWithText("Propose 1 chapters").assertIsNotEnabled()
    }

    @Test
    fun `an artifact with no version is told, and offered nothing`() = render(
        state(artifactVersion = null)
    ) {
        onNodeWithText("This artifact has no version", substring = true).assertIsDisplayed()
        onNodeWithText("Propose 1 chapters").assertIsNotEnabled()
    }

    @Test
    fun `every file blocked leaves the button offering nothing`() = render(
        state(
            chapters = listOf(
                row("binary", problem = UploadProblem.NotText),
                row("blank", problem = UploadProblem.Empty),
            )
        )
    ) {
        onNodeWithText("Propose 0 chapters").assertIsNotEnabled()
        onNodeWithText("Nothing in it to translate.").assertIsDisplayed()
    }

    @Test
    fun `a duplicate is said without being refused`() = render(
        state(chapters = listOf(row("copy", problem = UploadProblem.Duplicate("The wreck"))))
    ) {
        onNodeWithText("The same text as The wreck.", substring = true).assertIsDisplayed()
        // A book can repeat a page, so it is a warning and the button still works.
        onNodeWithText("Propose 1 chapters").assertIsEnabled()
    }

    @Test
    fun `a chapter too long for one session says how to fix it`() = render(
        state(chapters = listOf(row("long", problem = UploadProblem.TooManyChunks(80))))
    ) {
        onNodeWithText("80 chunks", substring = true).assertIsDisplayed()
        onNodeWithText("Split this file.", substring = true).assertIsDisplayed()
    }

    @Test
    fun `a proposed row waits, keeps its name, and cannot be removed`() = render(
        state(chapters = listOf(row("sent", sessionId = "session"), row("waiting")))
    ) {
        onNodeWithText("Waiting for the group to sign").assertIsDisplayed()
        // Its chapter is in front of the group: nothing about it is still a
        // decision, so there is no way to take it out of the list.
        onNodeWithContentDescription("Waiting for the group to sign").assertIsDisplayed()

        // And the button becomes a retry for what is left.
        onNodeWithText("Propose the remaining 1").assertIsEnabled()
    }

    @Test
    fun `a failed row is listed with its reason and can be tried again`() = render(
        state(
            chapters = listOf(
                row("sent", sessionId = "session"),
                row("failed", problem = UploadProblem.ProposalFailed),
            )
        )
    ) {
        onNodeWithText("Proposing this one failed. Try again.").assertIsDisplayed()
        // The only place a retry can be aimed from.
        onNodeWithText("Propose the remaining 1").assertIsEnabled()
    }

    private fun state(
        chapters: List<PickedChapter> = listOf(row("one")),
        canSign: Boolean = true,
        artifactVersion: MantraArtifactVersion? = version,
        baseIndex: Int = 0,
    ) = UploadChaptersUIState.Loaded(
        localChatRoom = room,
        artifact = artifact,
        artifactVersion = artifactVersion,
        canSign = canSign,
        chapters = chapters,
        baseIndex = baseIndex,
    )

    /**
     * Tall on purpose: the rows are cards in a `LazyColumn` and an assertion
     * about one that has not been scrolled to is an assertion about nothing.
     */
    private fun render(
        uiState: UploadChaptersUIState,
        assertions: ComposeUiTest.() -> Unit,
    ) = runDesktopComposeUiTest(width = 420, height = 2400) {
        setContent {
            MantraTheme {
                ProvideSnackbarHost {
                    Box(modifier = Modifier.fillMaxSize()) {
                        UploadChaptersScreen(
                            activeUserPublicKey = "a".repeat(64),
                            artifactId = "artifactId",
                            chatRoomId = "room",
                            relayHint = null,
                            initialUIState = uiState,
                            mantraRepository = MantraRepository.NO_OP_MANTRA_REPOSITORY,
                            chatRepository = ChatRepository.NO_OP_CHAT_REPOSITORY,
                            frostSigningRepository =
                                FrostSigningRepository.NO_OP_FROST_SIGNING_REPOSITORY,
                            onNavigateToRouteAndPopUpInclusive = {},
                            onNavigateBack = {},
                        )
                    }
                }
            }
        }

        assertions()
    }
}
