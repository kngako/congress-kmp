package press.mantra.compose.text

import com.vitorpamplona.quartz.nip01Core.core.Event
import press.mantra.compose.nostr.nip30303.ProjectArtifactVersionEvent
import press.mantra.compose.nostr.nip30303.ProjectEvent
import press.mantra.compose.nostr.nip30303.ProjectTranslationArtifactVersionEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How an event a group signs is said to the member signing it.
 *
 * These are read on the signing screen and in the room's list of proposals, and
 * the same words in both is the point of there being one mapping. What is pinned
 * here is the fallback: a kind the mapping has no words for is named by its
 * number over its content, and never described wrongly.
 */
class ProposedEventTest {

    @Test
    fun `a kind with no words of its own keeps its number`() {
        // Refusing to describe an event is better than describing it wrongly.
        val summary = ProposedEvent.summarize(event(30023, content = "An article"))

        assertEquals("Event of kind 30023", summary.label)
        assertEquals("An article", summary.detail)
    }

    // --- projects ---
    //
    // A project says who started it and who owns it, and both are claims the
    // group is being asked to put its signature to rather than facts anybody
    // checks. The creator signs nothing; the owners sign nothing. So the only
    // thing standing between "the group attested that X started this" and "the
    // proposer typed X" is whether the member signing could see the claim. That
    // is what these pin.

    private val roomId = "r".repeat(64)
    private val creator = "c".repeat(64)
    private val outsider = "d".repeat(64)

    private fun project(owners: Set<String>) = ProjectEvent.build(
        name = "Gospel of John",
        description = "The four chapters.",
        creatorPublicKey = creator,
        ownerPublicKeys = owners,
        createdAt = 1_700_000_000,
    ).let {
        Event("a".repeat(64), roomId, it.createdAt, it.kind, it.tags, it.content, "")
    }

    @Test
    fun `a project names itself and whoever started it`() {
        val summary = ProposedEvent.summarize(project(setOf(roomId)), chatRoomId = roomId)

        assertEquals("New project", summary.label)
        assertTrue(summary.detail.contains("Gospel of John"))
        assertTrue(summary.detail.contains("The four chapters."))
        assertTrue(summary.detail.contains("started by ${creator.take(12)}"))
    }

    @Test
    fun `a project owned by the room it is in says nothing about owners`() {
        // The ordinary case. Saying "owned by this group" every time is how a
        // member learns to stop reading the line.
        val summary = ProposedEvent.summarize(project(setOf(roomId)), chatRoomId = roomId)

        assertTrue(!summary.detail.contains("owned by"), summary.detail)
    }

    @Test
    fun `a project owned by somebody else says who, and not the room`() {
        val summary = ProposedEvent.summarize(
            project(setOf(roomId, outsider)),
            chatRoomId = roomId
        )

        assertTrue(summary.detail.contains("owned by"), summary.detail)
        assertTrue(summary.detail.contains(outsider.take(12)), summary.detail)
        assertTrue(!summary.detail.contains(roomId.take(12)), summary.detail)
    }

    @Test
    fun `with no room to compare against, every owner is named`() {
        val summary = ProposedEvent.summarize(project(setOf(roomId, outsider)))

        assertTrue(summary.detail.contains(roomId.take(12)), summary.detail)
        assertTrue(summary.detail.contains(outsider.take(12)), summary.detail)
    }

    @Test
    fun `a resolver turns the keys into names`() {
        val names = mapOf(creator to "Kgothatso", outsider to "Exonumia")

        val summary = ProposedEvent.summarize(
            project(setOf(roomId, outsider)),
            chatRoomId = roomId,
            resolve = names::get
        )

        assertTrue(summary.detail.contains("started by Kgothatso"), summary.detail)
        assertTrue(summary.detail.contains("owned by Exonumia"), summary.detail)
    }

    @Test
    fun `a membership is a hash without a resolver and a name with one`() {
        val versionId = "f".repeat(64)
        val projectId = "e".repeat(64)

        val source = ProjectArtifactVersionEvent.build(
            projectId = projectId,
            artifactVersionId = versionId,
            createdAt = 1_700_000_000,
        ).let {
            Event("a".repeat(64), roomId, it.createdAt, it.kind, it.tags, it.content, "")
        }

        // What a member sees in a founding batch, where the project is being
        // signed on the line above and resolves to nothing because nothing has
        // applied it yet.
        val bare = ProposedEvent.summarize(source, chatRoomId = roomId)
        assertEquals("A source version joins the project", bare.label)
        assertEquals(versionId.take(12), bare.detail)

        // And what it has to look like before a membership is proposed on its
        // own, which is the whole of what is being agreed to there.
        val named = ProposedEvent.summarize(
            source,
            chatRoomId = roomId,
            resolve = mapOf(versionId to "To Kill a Mockingbird 1.0", projectId to "Gospel of John")::get
        )
        assertEquals("To Kill a Mockingbird 1.0 · to Gospel of John", named.detail)
    }

    @Test
    fun `a translation joining a project is named as a translation`() {
        val translationId = "f".repeat(64)

        val membership = ProjectTranslationArtifactVersionEvent.build(
            projectId = "e".repeat(64),
            translationArtifactVersionId = translationId,
            createdAt = 1_700_000_000,
        ).let {
            Event("a".repeat(64), roomId, it.createdAt, it.kind, it.tags, it.content, "")
        }

        val summary = ProposedEvent.summarize(membership, chatRoomId = roomId)

        assertEquals("A translation joins the project", summary.label)
        assertEquals(translationId.take(12), summary.detail)
    }

    private fun event(
        kind: Int,
        tags: Array<Array<String>> = emptyArray(),
        content: String = "",
    ) = Event(
        id = "a".repeat(64),
        pubKey = "b".repeat(64),
        createdAt = 1_700_000_000,
        kind = kind,
        tags = tags,
        content = content,
        sig = "c".repeat(128),
    )
}
