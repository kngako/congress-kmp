package press.mantra.compose.text

import com.vitorpamplona.quartz.nip01Core.core.Event
import press.mantra.compose.nostr.nip30303.ProjectArtifactVersionEvent
import press.mantra.compose.nostr.nip30303.DialectEvent
import press.mantra.compose.nostr.nip30303.ProjectEvent
import press.mantra.compose.nostr.nip30303.ProjectTranslationArtifactVersionEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import press.mantra.compose.nostr.nip30303.ChapterEvent
import press.mantra.compose.nostr.nip30303.ChunkEvent
import press.mantra.compose.nostr.frost.GroupKeyStateEvent
import press.mantra.compose.managers.SubgroupOrigin
import press.mantra.compose.nostr.subgroup.SubgroupBirthCertificateEvent
import press.mantra.compose.nostr.subgroup.SubgroupLineage
import press.mantra.compose.nostr.subgroup.SubgroupParentage

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
    fun `a chapter is named measured and counted`() {
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
    fun `the chunk count is the batch's not a re-reading of the text`() {
        val summary = ProposedEvent.summarize(
            chapter(originalText = "One.\n\nTwo."),
            chunkCount = 5,
        )

        assertTrue(summary.detail.endsWith("5 chunks"), summary.detail)
    }

    @Test
    fun `with no batch to count a chapter says nothing about chunks`() {
        // A transcript line, or a signed event on its own: there is no batch to
        // ask, and guessing is what this parameter exists to stop.
        val summary = ProposedEvent.summarize(chapter(name = "The wreck"))

        assertEquals("The wreck · 12 words", summary.detail)
    }

    @Test
    fun `a chunk is said as its own words in its own place`() {
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
    fun `a project owned by somebody else says who and not the room`() {
        val summary = ProposedEvent.summarize(
            project(setOf(roomId, outsider)),
            chatRoomId = roomId
        )

        assertTrue(summary.detail.contains("owned by"), summary.detail)
        assertTrue(summary.detail.contains(outsider.take(12)), summary.detail)
        assertTrue(!summary.detail.contains(roomId.take(12)), summary.detail)
    }

    @Test
    fun `with no room to compare against every owner is named`() {
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

    @Test
    fun `a key state with no lineage reads as the key alone`() {
        // Every group made before subgroups, and every top-level group made
        // after. The path and the ceremony are what a signer needs, because the
        // only question is which key the room signs with.
        val summary = ProposedEvent.summarize(
            event(
                GroupKeyStateEvent.KIND,
                tags = GroupKeyStateEvent.assembleTags(room, "ceremony-1")
            )
        )

        assertEquals("This group's shared key", summary.label)
        assertEquals("m/9420/0/0 · ceremony-1", summary.detail)
    }

    @Test
    fun `a key state that names a parent says so first`() {
        // The one change in this mapping that is not cosmetic. A key state can be
        // re-announced now, and that is how a group is adopted -- so a member
        // approving this is deciding whether their group should answer to
        // another, and a derivation path answers a question nobody asked.
        val summary = ProposedEvent.summarize(
            event(
                GroupKeyStateEvent.KIND,
                tags = GroupKeyStateEvent.assembleTags(
                    chatRoomId = room,
                    dkgSessionId = "ceremony-1",
                    lineage = SubgroupLineage.of(parentage(parent))
                )
            )
        )

        assertEquals("This group's shared key, and the group it answers to", summary.label)
        assertEquals("${parent.take(16)} · m/9420/0/0 · ceremony-1", summary.detail)
    }

    @Test
    fun `a key state naming two parents counts them`() {
        val summary = ProposedEvent.summarize(
            event(
                GroupKeyStateEvent.KIND,
                tags = GroupKeyStateEvent.assembleTags(
                    chatRoomId = room,
                    dkgSessionId = "ceremony-1",
                    lineage = SubgroupLineage.of(listOf(parentage(parent), parentage(second)))
                )
            )
        )

        assertEquals("This group's shared key, and the 2 groups it answers to", summary.label)
        // In the lineage's canonical order, so two devices describe one proposal
        // the same way.
        assertEquals(
            "${parent.take(16)} · ${second.take(16)} · m/9420/0/0 · ceremony-1",
            summary.detail
        )
    }

    @Test
    fun `a key state claiming a lineage it cannot state reads as the key alone`() {
        // A half-claim, which `GroupKeyStateManager.stateFrom` would drop whole.
        // The summary cannot say "and one group" about a parent it could not
        // read, so it says nothing about lineage rather than guessing.
        val summary = ProposedEvent.summarize(
            event(
                GroupKeyStateEvent.KIND,
                tags = GroupKeyStateEvent.assembleTags(room, "ceremony-1") +
                    arrayOf(arrayOf("parent_group", parent))
            )
        )

        assertEquals("This group's shared key", summary.label)
    }

    private val room = "1".repeat(64)
    private val parent = "a".repeat(64)
    private val second = "b".repeat(64)

    /** A parentage whose certificate is nonsense; this mapping checks nothing. */
    private fun parentage(parentChatRoomId: String) = SubgroupParentage(
        parentChatRoomId = parentChatRoomId,
        certificate = event(SubgroupBirthCertificateEvent.KIND, content = parentChatRoomId)
    )

    // --- what a subgroup is for, on the certificate a quorum is asked to sign ---

    /** The parent doing the certifying. Its own id, as the author would be. */
    private val certifyingParent = "9".repeat(64)

    /**
     * The line that pays for the origin tag.
     *
     * A parent admin approving a certificate is putting the group's signature to
     * a room id produced by a ceremony most of them were not in. A name and a
     * head count say how big the thing is; this says what it does, and it is the
     * only line on the screen that does.
     */
    @Test
    fun `a certificate says what the subgroup is for, by name where the row is held`() {
        val projectId = "a1".padEnd(64, '0')

        val summary = ProposedEvent.summarize(
            event = event(
                kind = SubgroupBirthCertificateEvent.KIND,
                tags = SubgroupBirthCertificateEvent.assembleTags(
                    subgroupChatRoomId = "d".repeat(64),
                    parentChatRoomId = certifyingParent,
                    thresholdPublicKey = "02" + "e".repeat(64),
                    adminPublicKeys = listOf("1".repeat(64), "2".repeat(64), "3".repeat(64)),
                    name = "#Setswana Luke",
                    origin = SubgroupOrigin.Ref(kind = ProjectEvent.KIND, id = projectId)
                ),
                content = "d".repeat(64)
            ),
            resolve = { if (it == projectId) "Gospel of John" else null }
        )

        assertEquals("A new subgroup of this group", summary.label)
        assertEquals(
            "#Setswana Luke · for the project Gospel of John · run by 3 members",
            summary.detail
        )
    }

    /**
     * Unresolvable is a fact to state, not a gap to hide. A certificate may name
     * a row the reader will never hold -- these entities live in the parent's
     * tables and a subgroup's members need never have been in the parent -- so
     * the kind is still said and the id is truncated, which is what the rest of
     * the app does with something it has not seen.
     */
    @Test
    fun `an unresolvable origin still says what kind of thing it is`() {
        val summary = ProposedEvent.summarize(
            event = event(
                kind = SubgroupBirthCertificateEvent.KIND,
                tags = SubgroupBirthCertificateEvent.assembleTags(
                    subgroupChatRoomId = "d".repeat(64),
                    parentChatRoomId = certifyingParent,
                    thresholdPublicKey = "02" + "e".repeat(64),
                    adminPublicKeys = listOf("1".repeat(64)),
                    name = "#Sesotho",
                    origin = SubgroupOrigin.Ref(
                        kind = DialectEvent.KIND,
                        id = "beef".padEnd(64, '0')
                    )
                ),
                content = "d".repeat(64)
            )
        )

        assertTrue(
            summary.detail.contains("for the dialect beef00000000"),
            "the detail did not name the kind and the short hash: ${summary.detail}"
        )
    }

    /**
     * A subgroup for nothing in particular says nothing about an origin, rather
     * than saying "for nothing". That is the group's own "Add subgroup", and it
     * is also every certificate signed before the tag existed.
     */
    @Test
    fun `a subgroup for nothing in particular reads as it always did`() {
        val summary = ProposedEvent.summarize(
            event = event(
                kind = SubgroupBirthCertificateEvent.KIND,
                tags = SubgroupBirthCertificateEvent.assembleTags(
                    subgroupChatRoomId = "d".repeat(64),
                    parentChatRoomId = certifyingParent,
                    thresholdPublicKey = "02" + "e".repeat(64),
                    adminPublicKeys = listOf("1".repeat(64), "2".repeat(64)),
                    name = "#Translators"
                ),
                content = "d".repeat(64)
            )
        )

        assertEquals("#Translators · run by 2 members", summary.detail)
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
