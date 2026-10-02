package press.mantra.compose.text

import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import press.mantra.compose.nostr.nip30303.ChapterEvent
import press.mantra.compose.nostr.nip30303.ChunkEvent

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

    /**
     * A chapter is the longest thing a group is asked to sign, and the one whose
     * summary cannot be the thing itself.
     *
     * So the summary stands in for it: the name, how many words, and how many
     * chunks came out of the text -- which is the rest of what the batch
     * contains and so the rest of what is being agreed to.
     */
    @Test
    fun `a chapter is named, measured, and counted`() {
        val summary = ProposedEvent.summarize(chapter(name = "The wreck"), chunkCount = 4)

        assertEquals("New chapter", summary.label)
        assertEquals("The wreck · 12 words · 4 chunks", summary.detail)
    }

    @Test
    fun `one chunk is said in the singular`() {
        val summary = ProposedEvent.summarize(chapter(), chunkCount = 1)

        assertTrue(summary.detail.endsWith("1 chunk"), summary.detail)
    }

    /**
     * The count comes from the batch, not from the text.
     *
     * It used to be derived by re-splitting the chapter's own markdown, which is
     * a count of the batch dressed as a fact about the text: across a splitter
     * change the figure a member reads would disagree with the events they are
     * approving, because the batch was built by the proposer's splitter and
     * counted by the reader's. This asserts the summary reports what it was
     * handed -- the text here splits into two, and the batch says five.
     */
    @Test
    fun `the chunk count is the batch's, not a re-reading of the text`() {
        val summary = ProposedEvent.summarize(
            chapter(originalText = "One.\n\nTwo."),
            chunkCount = 5,
        )

        assertTrue(summary.detail.endsWith("5 chunks"), summary.detail)
    }

    @Test
    fun `with no batch to count, a chapter says nothing about chunks`() {
        // A transcript line, or a signed event on its own: there is no batch to
        // ask, and guessing is what this parameter exists to stop.
        val summary = ProposedEvent.summarize(chapter(name = "The wreck"))

        assertEquals("The wreck · 12 words", summary.detail)
    }

    @Test
    fun `a chunk is said as its own words, in its own place`() {
        // The chunks travel in the same batch as their chapter and are read
        // under it, so each has to be checkable on its own.
        val summary = ProposedEvent.summarize(
            ChunkEvent.build(
                chapterId = "d".repeat(64),
                text = "It began badly.",
                index = 2,
                wordCount = 3,
                characterCount = 15,
            ).let { template ->
                Event(
                    id = "a".repeat(64),
                    pubKey = "b".repeat(64),
                    createdAt = template.createdAt,
                    kind = template.kind,
                    tags = template.tags,
                    content = template.content,
                    sig = "",
                )
            }
        )

        assertTrue(summary.detail.contains("It began badly."), summary.detail)
    }

    /** A chapter as the group would be asked to sign it. */
    private fun chapter(
        name: String = "The wreck",
        originalText: String = "One two three.\n\nFour five six.\n\nSeven eight nine.\n\nTen eleven twelve.",
    ): Event {
        val template = ChapterEvent.build(
            artifactVersionId = "e".repeat(64),
            name = name,
            originalText = originalText,
            index = 0,
            wordCount = Markdown.wordCount(originalText),
            characterCount = Markdown.characterCount(originalText),
        )
        return Event(
            id = "a".repeat(64),
            pubKey = "b".repeat(64),
            createdAt = template.createdAt,
            kind = template.kind,
            tags = template.tags,
            content = template.content,
            sig = "",
        )
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
