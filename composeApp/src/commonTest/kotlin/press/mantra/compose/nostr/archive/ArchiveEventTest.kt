package press.mantra.compose.nostr.archive

import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import press.mantra.compose.nostr.archive.tags.ArchiveIdTag
import press.mantra.compose.nostr.archive.tags.ArchivePageTag
import press.mantra.compose.nostr.archive.tags.ArchiveRecipientTag
import press.mantra.compose.nostr.frost.FrostSigningEvents
import press.mantra.compose.nostr.frost.GroupKeyStateEvent
import press.mantra.compose.nostr.nip30303.ArtifactEvent
import press.mantra.compose.nostr.nip30303.ArtifactVersionEvent
import press.mantra.compose.nostr.nip30303.ChapterEvent
import press.mantra.compose.nostr.nip30303.ChunkEvent
import press.mantra.compose.nostr.nip30303.DialectEvent
import press.mantra.compose.nostr.nip30303.SubmissionEvent
import press.mantra.compose.nostr.nip30303.TranslationArtifactVersionContributorListEvent
import press.mantra.compose.nostr.nip30303.TranslationArtifactVersionEvent
import press.mantra.compose.nostr.nip30303.TranslationChapterContributorListEvent
import press.mantra.compose.nostr.nip30303.TranslationChapterEvent
import press.mantra.compose.nostr.nip30303.TranslationChunkEvent
import press.mantra.compose.nostr.nip30303.TranslationContributorListEvent
import press.mantra.compose.nostr.nip30303.TranslationEvent

/**
 * The envelope an archive travels in: what it carries, what it refuses, and the
 * order it puts things in.
 *
 * Nothing here verifies a signature -- that is `GroupKeyStateTest`'s half, and
 * `ArchiveManager`'s at apply time. What these cover is the framing, and the two
 * bounds a page has to hold whoever sent it.
 */
class ArchiveEventTest {
    private val recipient = "d".repeat(64)
    private val archiveId = "e".repeat(64)

    private fun payload(
        kind: Int,
        content: String = "payload",
        id: String = "a".repeat(63) + (kind % 10),
    ) = Event(
        id = id,
        pubKey = "b".repeat(64),
        createdAt = 1_700_000_000L,
        kind = kind,
        tags = arrayOf(arrayOf("alt", "test")),
        content = content,
        sig = "c".repeat(128)
    )

    // ---- The codec -------------------------------------------------------

    @Test
    fun `what is encoded is what comes back, whole`() {
        val events = listOf(
            payload(DialectEvent.KIND, "isiZulu"),
            payload(ArtifactEvent.KIND, "an artifact"),
        )

        val decoded = ArchiveEvent.decodePage(ArchiveEvent.encodePage(events))

        assertNotNull(decoded)
        assertEquals(events.map { it.content }, decoded.map { it.content })
        // The point of carrying whole events rather than rebuilt ones: the
        // signature is what the receiver checks, so losing it in transit would
        // leave them nothing to check.
        assertEquals(events.map { it.id }, decoded.map { it.id })
        assertEquals(events.map { it.pubKey }, decoded.map { it.pubKey })
        assertEquals(events.map { it.sig }, decoded.map { it.sig })
    }

    @Test
    fun `a page of one is still an array`() {
        // The contrast with FrostSigningEvents.encodeProposal, which keeps a bare
        // object for a batch of one so that builds predating batching can read
        // it. Both archive kinds are new, so there is no such build and no reason
        // to carry the second shape.
        val encoded = ArchiveEvent.encodePage(listOf(payload(DialectEvent.KIND)))

        assertTrue(encoded.startsWith("["), "a page is always an array")
        assertTrue(encoded.endsWith("]"))
        assertEquals(1, ArchiveEvent.decodePage(encoded)?.size)
    }

    @Test
    fun `a page that is not an array is not a page`() {
        assertNull(ArchiveEvent.decodePage(payload(DialectEvent.KIND).toJson()))
        assertNull(ArchiveEvent.decodePage(""))
        assertNull(ArchiveEvent.decodePage("not json"))
        assertNull(ArchiveEvent.decodePage("[unclosed"))
    }

    @Test
    fun `an empty page is refused rather than read as an empty archive`() {
        assertNull(ArchiveEvent.decodePage("[]"))
    }

    @Test
    fun `one unreadable payload refuses the whole page`() {
        // All-or-nothing here, and per-payload at verify time. They answer
        // different questions: a page that will not parse has lost its framing,
        // so a page quietly shortened by one element would report a complete
        // archive on its page count while holding less than it says. A payload
        // whose signature does not verify is a well-framed page with one bad
        // event in it, and dropping its honest neighbours would let one forgery
        // deny an entire archive.
        val good = payload(DialectEvent.KIND)

        assertNull(ArchiveEvent.decodePage("[${good.toJson()},\"not an event\"]"))
        assertNull(ArchiveEvent.decodePage("[\"not an event\",${good.toJson()}]"))
    }

    // ---- The two caps ----------------------------------------------------

    @Test
    fun `a page over the event cap is refused on the way in`() {
        val overCap = (0..ArchiveEvent.MAX_PAGE_EVENTS).map {
            payload(DialectEvent.KIND, id = it.toString().padStart(64, '0'))
        }
        assertEquals(ArchiveEvent.MAX_PAGE_EVENTS + 1, overCap.size)

        assertNull(ArchiveEvent.decodePage(ArchiveEvent.encodePage(overCap)))

        // And exactly at the cap is fine, so the bound is not off by one.
        assertNotNull(ArchiveEvent.decodePage(ArchiveEvent.encodePage(overCap.dropLast(1))))
    }

    @Test
    fun `the event cap can actually be reached under the byte cap`() {
        // The cap that cannot fire. An event is over 300 bytes before it says
        // anything -- 64 characters of id, 64 of pubkey, 128 of signature -- so a
        // count cap set above what MAX_PAGE_BYTES can hold is a check that never
        // runs, and the page-over-the-cap test above passes for the wrong reason.
        // Both caps have to be re-sized together; this is what says so.
        val atCap = (0 until ArchiveEvent.MAX_PAGE_EVENTS).map {
            payload(DialectEvent.KIND, id = it.toString().padStart(64, '0'))
        }
        val page = ArchiveEvent.encodePage(atCap)

        assertTrue(
            page.encodeToByteArray().size <= ArchiveEvent.MAX_PAGE_BYTES,
            "MAX_PAGE_EVENTS payloads take ${page.encodeToByteArray().size} bytes, " +
                "over the ${ArchiveEvent.MAX_PAGE_BYTES}-byte cap: the event cap can never fire"
        )
        assertEquals(ArchiveEvent.MAX_PAGE_EVENTS, ArchiveEvent.decodePage(page)?.size)
    }

    @Test
    fun `a page over the byte cap is refused on the way in`() {
        // Checked before the JSON is parsed, because the cap exists to bound the
        // work a remote party can ask this device to do. An archive is the second
        // place in this protocol where somebody else sets that size.
        val huge = ArchiveEvent.encodePage(
            listOf(payload(DialectEvent.KIND, content = "x".repeat(ArchiveEvent.MAX_PAGE_BYTES)))
        )
        assertTrue(huge.encodeToByteArray().size > ArchiveEvent.MAX_PAGE_BYTES)

        assertNull(ArchiveEvent.decodePage(huge))
    }

    @Test
    fun `the byte cap counts bytes, not characters`() {
        // A multi-byte payload that fits as characters and does not as bytes. If
        // this were measured in characters the cap would let through up to four
        // times what it says.
        val wide = "世".repeat(ArchiveEvent.MAX_PAGE_BYTES / 2)
        val page = ArchiveEvent.encodePage(listOf(payload(DialectEvent.KIND, content = wide)))

        assertTrue(page.length < ArchiveEvent.MAX_PAGE_BYTES)
        assertTrue(page.encodeToByteArray().size > ArchiveEvent.MAX_PAGE_BYTES)
        assertNull(ArchiveEvent.decodePage(page))
    }

    // ---- What an archive may carry ---------------------------------------

    @Test
    fun `the allowlist is every kind an archive can restore, and only those`() {
        listOf(
            DialectEvent.KIND,
            ArtifactEvent.KIND,
            ArtifactVersionEvent.KIND,
            ChapterEvent.KIND,
            ChunkEvent.KIND,
            TranslationArtifactVersionEvent.KIND,
            TranslationChapterEvent.KIND,
            TranslationChunkEvent.KIND,
            TranslationEvent.KIND,
        ).forEach { assertTrue(ArchiveEvent.isArchivable(it), "kind $it should be archivable") }

        assertEquals(9, ArchiveEvent.ARCHIVABLE_KINDS.size)
    }

    @Test
    fun `an archive may not carry a key state, however well signed`() {
        // The attack the allowlist exists for, and the reason it is an allowlist
        // rather than a denylist. A GroupKeyStateEvent is signed by the room and
        // passes verification perfectly, so an archive carrying an old one is a
        // validly signed statement about what the room signs with, replayed by
        // whoever kept a copy. Nothing but this stops it.
        assertFalse(ArchiveEvent.isArchivable(GroupKeyStateEvent.KIND))
        assertNull(ArchiveEvent.applyRank(GroupKeyStateEvent.KIND))
    }

    @Test
    fun `an archive carries documents, not envelopes or protocol`() {
        assertFalse(ArchiveEvent.isArchivable(SubmissionEvent.KIND))
        assertFalse(ArchiveEvent.isArchivable(ArchiveEvent.KIND))
        assertFalse(ArchiveEvent.isArchivable(ArchiveRequestEvent.KIND))
        FrostSigningEvents.ALL.forEach {
            assertFalse(ArchiveEvent.isArchivable(it), "frost kind $it")
        }
    }

    @Test
    fun `the contributor lists are left out while nothing applies them`() {
        // Not an oversight. ChatMessage.applyInnerEvent has no arm that writes a
        // row for any of these, so archiving them would cost bytes and restore
        // nothing. They belong in the list on the day that changes.
        assertFalse(ArchiveEvent.isArchivable(TranslationArtifactVersionContributorListEvent.KIND))
        assertFalse(ArchiveEvent.isArchivable(TranslationChapterContributorListEvent.KIND))
        assertFalse(ArchiveEvent.isArchivable(TranslationContributorListEvent.KIND))
    }

    // ---- Dependency order ------------------------------------------------

    @Test
    fun `the order is the foreign keys, not the kind numbers`() {
        fun rank(kind: Int) = assertNotNull(ArchiveEvent.applyRank(kind), "kind $kind has no rank")

        // Every one of these is a foreign key in Room, so an archive applied the
        // other way round is a constraint violation rather than a wrong answer.
        assertTrue(rank(DialectEvent.KIND) < rank(ArtifactEvent.KIND))
        assertTrue(rank(ArtifactEvent.KIND) < rank(ArtifactVersionEvent.KIND))
        assertTrue(rank(ArtifactVersionEvent.KIND) < rank(ChapterEvent.KIND))
        assertTrue(rank(ChapterEvent.KIND) < rank(ChunkEvent.KIND))
        assertTrue(rank(ArtifactVersionEvent.KIND) < rank(TranslationArtifactVersionEvent.KIND))
        assertTrue(rank(DialectEvent.KIND) < rank(TranslationArtifactVersionEvent.KIND))
        assertTrue(rank(TranslationArtifactVersionEvent.KIND) < rank(TranslationChapterEvent.KIND))
        assertTrue(rank(ChapterEvent.KIND) < rank(TranslationChapterEvent.KIND))
        assertTrue(rank(ChunkEvent.KIND) < rank(TranslationChunkEvent.KIND))
        assertTrue(rank(TranslationChapterEvent.KIND) < rank(TranslationChunkEvent.KIND))
        assertTrue(rank(TranslationChunkEvent.KIND) < rank(TranslationEvent.KIND))
        assertTrue(rank(TranslationArtifactVersionEvent.KIND) < rank(TranslationEvent.KIND))

        // And the numbers really do disagree with the order, which is why this is
        // a list rather than a sortedBy { kind }.
        assertTrue(DialectEvent.KIND > ArtifactEvent.KIND)
        assertTrue(rank(DialectEvent.KIND) < rank(ArtifactEvent.KIND))
    }

    @Test
    fun `sorting a page puts what is referenced before what refers to it`() {
        val jumbled = listOf(
            payload(TranslationEvent.KIND),
            payload(ChunkEvent.KIND),
            payload(DialectEvent.KIND),
            payload(ChapterEvent.KIND),
            payload(ArtifactEvent.KIND),
            payload(ArtifactVersionEvent.KIND),
        )

        assertEquals(
            listOf(
                DialectEvent.KIND,
                ArtifactEvent.KIND,
                ArtifactVersionEvent.KIND,
                ChapterEvent.KIND,
                ChunkEvent.KIND,
                TranslationEvent.KIND,
            ),
            ArchiveEvent.inApplyOrder(jumbled).map { it.kind }
        )
    }

    @Test
    fun `two of a kind keep the order they were handed in`() {
        // Nothing orders two chapters of one version, and keeping the caller's
        // order is one less thing that differs between two members assembling the
        // same archive.
        val chapters = listOf(
            payload(ChapterEvent.KIND, "one", id = "1".repeat(64)),
            payload(ChapterEvent.KIND, "two", id = "2".repeat(64)),
            payload(ChapterEvent.KIND, "three", id = "3".repeat(64)),
        )

        assertEquals(
            listOf("one", "two", "three"),
            ArchiveEvent.inApplyOrder(chapters).map { it.content }
        )
    }

    // ---- The tags --------------------------------------------------------

    @Test
    fun `a page says which archive it is, where in it, and who for`() {
        val template = ArchiveEvent.build(
            payloads = listOf(payload(DialectEvent.KIND)),
            archiveId = archiveId,
            index = 2,
            count = 5,
            recipient = recipient,
            createdAt = 1_700_000_100L
        )

        val readBack = ArchiveEvent(
            id = "f".repeat(64),
            pubKey = "b".repeat(64),
            createdAt = template.createdAt,
            tags = template.tags,
            content = template.content,
            sig = ""
        )

        assertEquals(archiveId, readBack.archiveId())
        assertEquals(2, readBack.page()?.index)
        assertEquals(5, readBack.page()?.count)
        assertEquals(recipient, readBack.recipient())
        assertEquals(1, readBack.payloads()?.size)
    }

    @Test
    fun `a page outside its own archive is not a page`() {
        // Refused rather than clamped: the pair is how a receiver decides it has
        // everything, so a repaired one would let a truncated archive read as
        // complete.
        assertNull(ArchivePageTag.parse(arrayOf(ArchivePageTag.TAG_NAME, "5", "3")))
        assertNull(ArchivePageTag.parse(arrayOf(ArchivePageTag.TAG_NAME, "-1", "3")))
        assertNull(ArchivePageTag.parse(arrayOf(ArchivePageTag.TAG_NAME, "0", "0")))
        assertNull(ArchivePageTag.parse(arrayOf(ArchivePageTag.TAG_NAME, "0")))
        assertNull(ArchivePageTag.parse(arrayOf(ArchivePageTag.TAG_NAME, "one", "3")))

        assertEquals(0, ArchivePageTag.parse(arrayOf(ArchivePageTag.TAG_NAME, "0", "1"))?.index)
        assertTrue(ArchivePageTag.parse(arrayOf(ArchivePageTag.TAG_NAME, "0", "1"))!!.isOnlyPage())
    }

    @Test
    fun `an archive id and a recipient have to be there to be read`() {
        assertNull(ArchiveIdTag.parse(arrayOf(ArchiveIdTag.TAG_NAME)))
        assertNull(ArchiveIdTag.parse(arrayOf(ArchiveIdTag.TAG_NAME, " ")))
        assertNull(ArchiveIdTag.parse(arrayOf("other", archiveId)))
        assertEquals(archiveId, ArchiveIdTag.parse(arrayOf(ArchiveIdTag.TAG_NAME, archiveId))?.archiveId)

        assertNull(ArchiveRecipientTag.parse(arrayOf(ArchiveRecipientTag.TAG_NAME, "short")))
        assertEquals(
            recipient,
            ArchiveRecipientTag.parse(arrayOf(ArchiveRecipientTag.TAG_NAME, recipient))?.pubKey
        )
    }

    // ---- What build refuses to make --------------------------------------

    @Test
    fun `a page is not built over either cap, or out of what it may not carry`() {
        // The outbound half of both checks. It is politeness rather than
        // security -- the inbound one is the boundary -- but a page nobody can
        // read back is better failed where it was made.
        assertFailsWith<IllegalArgumentException> {
            ArchiveEvent.build(emptyList(), archiveId, 0, 1, recipient)
        }
        assertFailsWith<IllegalArgumentException> {
            ArchiveEvent.build(
                payloads = (0..ArchiveEvent.MAX_PAGE_EVENTS).map {
                    payload(DialectEvent.KIND, id = it.toString().padStart(64, '0'))
                },
                archiveId = archiveId,
                index = 0,
                count = 1,
                recipient = recipient
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ArchiveEvent.build(
                payloads = listOf(payload(GroupKeyStateEvent.KIND)),
                archiveId = archiveId,
                index = 0,
                count = 1,
                recipient = recipient
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ArchiveEvent.build(
                payloads = listOf(
                    payload(DialectEvent.KIND, content = "x".repeat(ArchiveEvent.MAX_PAGE_BYTES))
                ),
                archiveId = archiveId,
                index = 0,
                count = 1,
                recipient = recipient
            )
        }
    }

    @Test
    fun `a request carries nothing but what it is`() {
        val template = ArchiveRequestEvent.build(createdAt = 1_700_000_100L)

        assertEquals(ArchiveRequestEvent.KIND, template.kind)
        assertEquals("", template.content)
        // The room is the envelope and the asker is the MLS sender, so there is
        // nothing left for the event itself to say.
        assertEquals(1, template.tags.size)
        assertEquals("alt", template.tags[0][0])
    }
}
