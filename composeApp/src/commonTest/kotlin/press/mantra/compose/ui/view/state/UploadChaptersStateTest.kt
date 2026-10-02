package press.mantra.compose.ui.view.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import press.mantra.compose.database.model.ChatRoom
import press.mantra.compose.database.model.MantraArtifact
import press.mantra.compose.database.model.MantraArtifactVersion
import press.mantra.compose.database.model.intermdiate.LocalChatRoom

/**
 * What the upload screen's button is allowed to offer.
 *
 * `canPropose` is read only by the FAB, which is exactly why it is worth a test
 * of its own: there is no other caller to notice if one of its four conditions
 * goes missing, and each of them exists because offering the action without it
 * would ask the group for something that cannot happen.
 */
class UploadChaptersStateTest {

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
        problem: UploadProblem? = null,
        sessionId: String? = null,
    ) = PickedChapter(
        id = id,
        fileName = "$id.md",
        name = id,
        markdown = "A paragraph.",
        chunks = listOf("A paragraph."),
        wordCount = 2,
        characterCount = 12,
        proposalBytes = 1000,
        problem = problem,
        sessionId = sessionId,
    )

    private fun state(
        chapters: List<PickedChapter> = listOf(row("one")),
        canSign: Boolean = true,
        artifactVersion: MantraArtifactVersion? = version,
        isProposing: Boolean = false,
    ) = UploadChaptersUIState.Loaded(
        localChatRoom = room,
        artifact = artifact,
        artifactVersion = artifactVersion,
        canSign = canSign,
        chapters = chapters,
        isProposing = isProposing,
    )

    @Test
    fun `a clean row with a version and a key can be proposed`() {
        assertTrue(state().canPropose)
    }

    @Test
    fun `nothing picked is nothing to propose`() {
        assertFalse(state(chapters = emptyList()).canPropose)
    }

    @Test
    fun `a group with no shared key cannot sign a chapter into existence`() {
        assertFalse(state(canSign = false).canPropose)
    }

    @Test
    fun `an artifact with no version has nothing for a chapter to hang off`() {
        assertFalse(state(artifactVersion = null).canPropose)
    }

    @Test
    fun `a pass already running is not offered a second time`() {
        // The guard against a double tap proposing every chapter twice, which
        // nothing could retract.
        assertFalse(state(isProposing = true).canPropose)
    }

    @Test
    fun `a blocking problem takes its own row out and leaves the others`() {
        val chapters = listOf(
            row("good"),
            row("binary", problem = UploadProblem.NotText),
            row("also-good"),
        )

        assertEquals(listOf("good", "also-good"), state(chapters).proposable.map { it.id })
        assertTrue(state(chapters).canPropose)
    }

    @Test
    fun `every row blocked is nothing to propose`() {
        val chapters = listOf(
            row("binary", problem = UploadProblem.NotText),
            row("empty", problem = UploadProblem.Empty),
        )

        assertEquals(emptyList(), state(chapters).proposable)
        assertFalse(state(chapters).canPropose)
    }

    @Test
    fun `a warning is not a block`() {
        // A book can repeat a page, so a duplicate is said and not refused.
        val chapters = listOf(row("copy", problem = UploadProblem.Duplicate("The Wreck")))

        assertEquals(listOf("copy"), state(chapters).proposable.map { it.id })
        assertTrue(state(chapters).canPropose)
    }

    @Test
    fun `a failed proposal is still proposable, because a retry is a new proposal`() {
        val chapters = listOf(row("tried", problem = UploadProblem.ProposalFailed))

        assertEquals(listOf("tried"), state(chapters).proposable.map { it.id })
        assertTrue(state(chapters).canPropose)
    }

    @Test
    fun `a row already proposed is neither proposable nor forgotten`() {
        val chapters = listOf(row("sent", sessionId = "session"), row("waiting"))
        val loaded = state(chapters)

        assertEquals(listOf("waiting"), loaded.proposable.map { it.id })
        assertEquals(listOf("sent"), loaded.proposed.map { it.id })
    }

    @Test
    fun `everything proposed leaves nothing to offer`() {
        val chapters = listOf(row("sent", sessionId = "session"))

        assertEquals(emptyList(), state(chapters).proposable)
        assertFalse(state(chapters).canPropose)
    }
}
