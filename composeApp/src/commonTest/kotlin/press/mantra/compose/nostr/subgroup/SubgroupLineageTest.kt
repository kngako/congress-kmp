package press.mantra.compose.nostr.subgroup

import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The two invariants a lineage holds, and the three edits it allows.
 *
 * Nothing here signs anything. `SubgroupLineage` is about the *shape* of a
 * parentage set -- no repeats, one canonical order -- and the certificates in it
 * are deliberately nonsense, because whether a certificate is good is
 * `SubgroupBirthCertificateEvent.certifies`' question and is asked on every read
 * rather than at the door. A test here that needed a real signature would be
 * testing the wrong class.
 */
class SubgroupLineageTest {
    private val alpha = "a".repeat(64)
    private val beta = "b".repeat(64)
    private val gamma = "c".repeat(64)

    @Test
    fun `no parents is no lineage rather than an empty one`() {
        assertNull(SubgroupLineage.of(emptyList()))
    }

    @Test
    fun `one parent cannot fail, because it has nothing to repeat`() {
        val lineage = SubgroupLineage.of(parentage(alpha))

        assertEquals(1, lineage.size)
        assertEquals(listOf(alpha), lineage.parentChatRoomIds)
        assertTrue(lineage.names(alpha))
        assertFalse(lineage.names(beta))
    }

    @Test
    fun `the same parent twice is refused rather than folded`() {
        // One parent asked twice, carrying two certificates. Folding them would
        // be this device picking which of the parent's statements counts.
        assertNull(SubgroupLineage.of(listOf(parentage(alpha), parentage(alpha))))
    }

    @Test
    fun `a repeat is a repeat whatever case it is written in`() {
        val lineage = SubgroupLineage.of(listOf(parentage(alpha), parentage(alpha.uppercase())))

        assertNull(lineage)
    }

    @Test
    fun `a room id is matched case-insensitively, because it is hex`() {
        val lineage = SubgroupLineage.of(parentage(alpha))

        assertTrue(lineage.names(alpha.uppercase()))
    }

    @Test
    fun `the order is the parents' ids, whatever order they arrived in`() {
        // The whole reason this class sorts: two devices assembling one lineage
        // from the same certificates have to emit the same bytes, or a reader
        // that pairs the first tag of each name gets a mismatched pair.
        val forwards = SubgroupLineage.of(listOf(parentage(alpha), parentage(beta), parentage(gamma)))
        val backwards = SubgroupLineage.of(listOf(parentage(gamma), parentage(beta), parentage(alpha)))

        assertEquals(listOf(alpha, beta, gamma), forwards?.parentChatRoomIds)
        assertEquals(listOf(alpha, beta, gamma), backwards?.parentChatRoomIds)
    }

    @Test
    fun `sorting is on the id and not on the case it is written in`() {
        val lineage = SubgroupLineage.of(listOf(parentage(beta.uppercase()), parentage(alpha)))

        // Sorted by the lowercased id, and the id itself is kept as it arrived --
        // the certificate is the authority on what the parent is called, and this
        // tag is only an index into it.
        assertEquals(listOf(alpha, beta.uppercase()), lineage?.parentChatRoomIds)
    }

    @Test
    fun `adding a parent is an addition, not a replacement`() {
        // A key state is replaceable and the newest wins wholesale, so an
        // adoption that carried only the new parent would silently drop the old
        // one. Expressing it as an addition is what stops a caller assembling
        // the resulting set by hand and forgetting half of it.
        val adopted = SubgroupLineage.of(parentage(beta)).plus(parentage(alpha))

        assertEquals(listOf(alpha, beta), adopted?.parentChatRoomIds)
    }

    @Test
    fun `adding a parent that is already there is refused`() {
        assertNull(SubgroupLineage.of(parentage(alpha)).plus(parentage(alpha)))
    }

    @Test
    fun `dropping the last parent leaves no lineage at all`() {
        // Null is "nobody's child", which is what a caller passes on. It revokes
        // nothing: the certificate is a signature and exists forever, and what
        // this removes is the child's half of the link.
        assertNull(SubgroupLineage.of(parentage(alpha)).minus(alpha))
    }

    @Test
    fun `dropping one of two leaves the other`() {
        val lineage = SubgroupLineage.of(listOf(parentage(alpha), parentage(beta)))

        assertEquals(listOf(beta), lineage?.minus(alpha)?.parentChatRoomIds)
        assertEquals(listOf(alpha), lineage?.minus(beta)?.parentChatRoomIds)
    }

    @Test
    fun `dropping a parent that was never there changes nothing`() {
        val lineage = SubgroupLineage.of(listOf(parentage(alpha), parentage(beta)))

        assertEquals(listOf(alpha, beta), lineage?.minus(gamma)?.parentChatRoomIds)
    }

    @Test
    fun `the certificates ride along with their own parents`() {
        // The pairing is the point of the type: an id and the certificate beside
        // it have to stay together through the sort, or `certifies` would be
        // asked about a pair the group never signed.
        val lineage = SubgroupLineage.of(listOf(parentage(gamma), parentage(alpha)))
        val parents = lineage?.parents.orEmpty()

        assertEquals(alpha, parents[0].parentChatRoomId)
        assertEquals(alpha, parents[0].certificate.content)
        assertEquals(gamma, parents[1].parentChatRoomId)
        assertEquals(gamma, parents[1].certificate.content)
    }

    @Test
    fun `a parentage is carried as it was handed over`() {
        val parentage = parentage(alpha)

        assertSame(parentage, SubgroupLineage.of(parentage).parents.single())
    }

    /**
     * A parentage whose certificate says only which parent it belongs to, so a
     * test can tell two of them apart after a sort. Not a valid certificate and
     * not meant to be one.
     */
    private fun parentage(parentChatRoomId: String) = SubgroupParentage(
        parentChatRoomId = parentChatRoomId,
        certificate = Event(
            id = "d".repeat(64),
            pubKey = parentChatRoomId,
            createdAt = 1_700_000_000,
            kind = SubgroupBirthCertificateEvent.KIND,
            tags = emptyArray(),
            content = parentChatRoomId,
            sig = "e".repeat(128)
        )
    )
}
