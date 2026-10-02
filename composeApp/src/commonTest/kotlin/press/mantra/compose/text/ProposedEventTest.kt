package press.mantra.compose.text

import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlin.test.Test
import kotlin.test.assertEquals
import press.mantra.compose.nostr.frost.GroupKeyStateEvent
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
