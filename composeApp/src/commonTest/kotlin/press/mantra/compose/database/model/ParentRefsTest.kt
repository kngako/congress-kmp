package press.mantra.compose.database.model

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import press.mantra.compose.nostr.nip30303.ArtifactEvent
import press.mantra.compose.nostr.nip30303.ArtifactVersionEvent
import press.mantra.compose.nostr.nip30303.ChapterEvent
import press.mantra.compose.nostr.nip30303.ChunkEvent
import press.mantra.compose.nostr.nip30303.DialectEvent
import press.mantra.compose.nostr.nip30303.TranslationArtifactVersionEvent
import press.mantra.compose.nostr.nip30303.TranslationChapterEvent
import press.mantra.compose.nostr.nip30303.TranslationChunkEvent
import press.mantra.compose.nostr.nip30303.tags.ArtifactIdTag

/**
 * What the inbound side must wait for before it writes a row.
 *
 * These have to match the foreign keys on the Mantra* entities exactly. A
 * parent claimed here that the schema does not enforce holds a payload back
 * for nothing; one the schema enforces but that is missing here is an insert
 * that violates a constraint, and SQLite answers that by rolling back the
 * whole inbound transaction -- losing the group event, the submission and the
 * transcript line, none of which is ever retried. So the mapping is asserted
 * rather than trusted to stay in step.
 */
class ParentRefsTest {
    private val author = "a".repeat(64)
    private val dialect = "d".repeat(64)
    private val artifact = "1".repeat(64)
    private val version = "2".repeat(64)
    private val chapter = "3".repeat(64)
    private val chunk = "4".repeat(64)
    private val translationVersion = "5".repeat(64)
    private val translationChapter = "6".repeat(64)

    private fun eventOf(template: EventTemplate<*>) = Event(
        id = "f".repeat(64),
        pubKey = author,
        createdAt = template.createdAt,
        kind = template.kind,
        tags = template.tags,
        content = template.content,
        sig = "",
    )

    private fun refsOf(template: EventTemplate<*>) =
        ChatMessage.parentRefsOf(eventOf(template)).map { it.id to it.kind }

    @Test
    fun `an artifact waits for its dialect`() {
        val refs = refsOf(
            ArtifactEvent.build(
                name = "In Detention",
                url = "example.com",
                visibility = "private",
                license = "cc",
                dialectId = dialect,
            )
        )

        assertEquals(listOf(dialect to DialectEvent.KIND), refs)
    }

    @Test
    fun `a version waits for its artifact`() {
        val refs = refsOf(
            ArtifactVersionEvent.build(content = "1.0") {
                addUnique(ArtifactIdTag.assemble(artifact))
            }
        )

        assertEquals(listOf(artifact to ArtifactEvent.KIND), refs)
    }

    @Test
    fun `a chapter waits for its version and a chunk for its chapter`() {
        val chapterRefs = refsOf(
            ChapterEvent.build(
                artifactVersionId = version,
                name = "Chapter 1",
                originalText = "text",
                index = 0,
                wordCount = 1,
                characterCount = 4,
            )
        )
        val chunkRefs = refsOf(
            ChunkEvent.build(
                chapterId = chapter,
                text = "text",
                index = 0,
                wordCount = 1,
                characterCount = 4,
            )
        )

        assertEquals(listOf(version to ArtifactVersionEvent.KIND), chapterRefs)
        assertEquals(listOf(chapter to ChapterEvent.KIND), chunkRefs)
    }

    @Test
    fun `a translation waits for both of its parents`() {
        val refs = refsOf(
            TranslationArtifactVersionEvent.build(
                artifactVersionId = version,
                dialectId = dialect,
                name = "Sesotho",
                visibility = "private",
                license = "cc",
            )
        )

        assertEquals(
            listOf(
                version to ArtifactVersionEvent.KIND,
                dialect to DialectEvent.KIND,
            ),
            refs
        )
    }

    @Test
    fun `a translated chapter and chunk each wait for both of their parents`() {
        val chapterRefs = refsOf(
            TranslationChapterEvent.build(
                translationArtifactVersionId = translationVersion,
                chapterId = chapter,
                index = 0,
            )
        )
        val chunkRefs = refsOf(
            TranslationChunkEvent.build(
                translationChapterId = translationChapter,
                chunkId = chunk,
                index = 0,
                text = "translated",
            )
        )

        assertEquals(
            listOf(
                translationVersion to TranslationArtifactVersionEvent.KIND,
                chapter to ChapterEvent.KIND,
            ),
            chapterRefs
        )
        assertEquals(
            listOf(
                translationChapter to TranslationChapterEvent.KIND,
                chunk to ChunkEvent.KIND,
            ),
            chunkRefs
        )
    }

    @Test
    fun `a dialect waits for nothing, so it can always start a group off`() {
        val refs = refsOf(
            DialectEvent.build(name = "Sesotho", country = "Lesotho", language = "st")
        )

        assertTrue(refs.isEmpty())
    }
}
