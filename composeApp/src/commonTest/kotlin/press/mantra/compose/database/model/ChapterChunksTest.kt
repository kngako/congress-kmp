package press.mantra.compose.database.model

import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import press.mantra.compose.nostr.nip30303.ChapterEvent
import press.mantra.compose.nostr.nip30303.tags.OriginalTextTag

/**
 * The chunks of a chapter are derived, not delivered.
 *
 * The group signs a chapter and nothing else, so the chunks it splits into are
 * not events anybody sent: every device builds those rows for itself out of the
 * chapter it already holds. That only works while every device builds the *same*
 * rows, and nothing about the ids would show it if they stopped — they are
 * content hashes, opaque hex either way. What would show is two members
 * translating what looks like the same paragraph into rows that never reconcile,
 * with the chapter identical on both screens.
 */
class ChapterChunksTest {
    private val groupKey = "a".repeat(64)
    private val artifactVersionId = "b".repeat(64)
    private val chatRoomId = "room"

    private val twoParagraphs = "The first paragraph.\n\nThe second paragraph."

    private fun signedChapter(
        name: String = "Chapter 1",
        originalText: String = twoParagraphs,
        index: Int = 0,
        createdAt: Long = 1_700_000_000,
    ): ChapterEvent {
        val template = ChapterEvent.build(
            artifactVersionId = artifactVersionId,
            name = name,
            originalText = originalText,
            index = index,
            wordCount = 6,
            characterCount = originalText.length,
            createdAt = createdAt,
        )

        // Hashed rather than made up, so two fixtures that differ are two
        // different chapters here for the same reason they would be in the app.
        return ChapterEvent(
            id = EventHasher.hashId(
                pubKey = groupKey,
                createdAt = template.createdAt,
                kind = template.kind,
                tags = template.tags,
                content = template.content
            ),
            pubKey = groupKey,
            createdAt = template.createdAt,
            tags = template.tags,
            content = template.content,
            sig = "d".repeat(128)
        )
    }

    @Test
    fun `the derived chunks are a function of the chapter and nothing else`() {
        // Every input has to come off the chapter. Reading the clock here would
        // still agree with itself twice in a row -- and disagree between two
        // devices that applied the same chapter minutes apart, which is the case
        // nobody can reproduce on demand. So the timestamps are checked against
        // the chapter's rather than against a second derivation.
        val chapter = signedChapter(createdAt = 1_700_000_000)

        val chunks = MantraChunk.chunksOf(chapter, chatRoomId)

        assertEquals(2, chunks.size)
        assertTrue(chunks.all { it.createdAt.epochSeconds == 1_700_000_000L })
    }

    @Test
    fun `two devices derive the same chunks from the same chapter`() {
        val chapter = signedChapter()

        val mine = MantraChunk.chunksOf(chapter, chatRoomId)
        val theirs = MantraChunk.chunksOf(chapter, chatRoomId)

        assertEquals(mine.map { it.id }, theirs.map { it.id })
        assertEquals(mine.map { it.createdAt }, theirs.map { it.createdAt })
    }

    @Test
    fun `a chapter signed at a different moment derives different chunks`() {
        // The chapter's own timestamp is bound into the derived ids, so two
        // proposals identical but for when they were made stay two chapters with
        // two sets of chunks rather than colliding on one set of rows.
        val first = MantraChunk.chunksOf(signedChapter(createdAt = 1_700_000_000), chatRoomId)
        val second = MantraChunk.chunksOf(signedChapter(createdAt = 1_700_000_001), chatRoomId)

        assertNotEquals(first.map { it.id }, second.map { it.id })
    }

    @Test
    fun `the derived chunks hang off the chapter and carry its text in order`() {
        val chapter = signedChapter()

        val chunks = MantraChunk.chunksOf(chapter, chatRoomId)

        assertEquals(listOf(chapter.id, chapter.id), chunks.map { it.chapterId })
        assertEquals(listOf("The first paragraph.", "The second paragraph."), chunks.map { it.text })
        assertEquals(listOf(0, 1), chunks.map { it.index })
        // Authored by whoever authored the chapter -- the group, once signed --
        // and unsigned, because nobody signed these.
        assertTrue(chunks.all { it.publicKey == groupKey })
        assertTrue(chunks.all { it.signature == "" })
    }

    @Test
    fun `each chunk counts its own paragraph rather than the chapter's`() {
        // The chapter's counts describe the whole text. A chunk that inherited
        // them would report every paragraph as the length of the chapter, and
        // any progress measured per chunk would be wrong by that factor.
        val chunks = MantraChunk.chunksOf(
            signedChapter(originalText = "One two three.\n\nFour."),
            chatRoomId
        )

        assertEquals(listOf(3, 1), chunks.map { it.wordCount })
        assertEquals(listOf("One two three.".length, "Four.".length), chunks.map { it.characterCount })
    }

    @Test
    fun `two identical paragraphs stay two chunks`() {
        // Ids are content hashes, so repeated text is where they would collide
        // and a chapter would silently lose a paragraph. The position is in the
        // event, which is what keeps them apart.
        val chunks = MantraChunk.chunksOf(
            signedChapter(originalText = "Again.\n\nAgain."),
            chatRoomId
        )

        assertEquals(2, chunks.size)
        assertNotEquals(chunks[0].id, chunks[1].id)
    }

    @Test
    fun `where a chapter sits does not change the chunks it splits into`() {
        // Two chapters with the same text at different positions in the book are
        // two chapters, and their chunks hang off their own chapter. The check
        // that matters is that the ids differ -- chunks of chapter three must not
        // be chunks of chapter one.
        val first = signedChapter(index = 0)
        val second = signedChapter(index = 3)

        val firstChunks = MantraChunk.chunksOf(first, chatRoomId)
        val secondChunks = MantraChunk.chunksOf(second, chatRoomId)

        assertNotEquals(first.id, second.id)
        assertEquals(firstChunks.map { it.text }, secondChunks.map { it.text })
        assertNotEquals(firstChunks.map { it.id }, secondChunks.map { it.id })
    }

    @Test
    fun `a chapter with no text to split derives no chunks`() {
        assertEquals(emptyList(), MantraChunk.chunksOf(signedChapter(originalText = "   "), chatRoomId))

        // And a chapter that declares no text at all, which is malformed rather
        // than empty: nothing to split, and nothing to throw over either.
        val declared = signedChapter()
        val silent = ChapterEvent(
            id = declared.id,
            pubKey = declared.pubKey,
            createdAt = declared.createdAt,
            tags = declared.tags.filterNot { it.firstOrNull() == OriginalTextTag.TAG_NAME }
                .toTypedArray(),
            content = declared.content,
            sig = declared.sig
        )

        assertEquals(emptyList(), MantraChunk.chunksOf(silent, chatRoomId))
    }

    @Test
    fun `a chapter that arrived as a submission derives nothing`() {
        // Chapters used to travel as a submission with a chunk event per
        // paragraph, and those rows are already on disk -- written under the
        // submitter's key, with ids that have nothing to do with these. Splitting
        // one again would leave every paragraph in it twice, and a replay of the
        // room's group events would do it without anybody adding anything.
        val submitted = signedChapter().let { chapter ->
            ChapterEvent(
                id = chapter.id,
                pubKey = chapter.pubKey,
                createdAt = chapter.createdAt,
                tags = chapter.tags,
                content = chapter.content,
                sig = "" // A rumor: what a submission carries.
            )
        }

        assertEquals(emptyList(), MantraChunk.chunksOf(submitted, chatRoomId))
    }

    @Test
    fun `the derived chunk row and its event agree on the id`() {
        // A chunk is read back off the wire by other code paths (an inbound
        // ChunkEvent from a peer), so the row a device derives has to be the row
        // that event would produce -- otherwise the same chunk arrives twice.
        val chunk = MantraChunk.chunksOf(signedChapter(), chatRoomId).first()

        assertEquals(chunk.id, chunk.toChunkEvent().id)
        assertEquals(
            chunk.id,
            EventHasher.hashId(
                pubKey = chunk.publicKey,
                createdAt = chunk.createdAt.epochSeconds,
                kind = chunk.toChunkEvent().kind,
                tags = chunk.toChunkEvent().tags,
                content = chunk.toChunkEvent().content
            )
        )
    }
}
