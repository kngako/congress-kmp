package press.mantra.compose.managers

import kotlin.test.Test
import kotlin.test.assertEquals
import press.mantra.compose.database.model.MantraArtifact
import press.mantra.compose.database.model.MantraDialect
import press.mantra.compose.database.model.MantraProject
import press.mantra.compose.database.model.MantraTranslationArtifactVersion
import press.mantra.compose.database.model.traits.SubgroupOriginEntity
import press.mantra.compose.nostr.nip30303.ArtifactEvent
import press.mantra.compose.nostr.nip30303.DialectEvent
import press.mantra.compose.nostr.nip30303.ProjectEvent
import press.mantra.compose.nostr.nip30303.TranslationArtifactVersionEvent

/**
 * Reading a subgroup out of the thing it is for.
 *
 * Four entities, one answer each, and the answer is three fields the row already
 * carried. What these cases are really guarding is that the three keep meaning
 * the same thing: the parent is the room the entity lives in and never the
 * entity's id, the name is bare and un-normalised, and the kind is the entity's
 * own kind rather than a subgroup kind.
 */
class SubgroupOriginTest {

    private val room = "r".repeat(64)
    private val entityId = "e".repeat(64)
    private val key = "p".repeat(64)

    private fun dialect(name: String = "Sesotho") = MantraDialect(
        id = entityId,
        publicKey = key,
        name = name,
        country = "Lesotho",
        language = "st",
        signature = "",
        chatRoomId = room,
    )

    private fun artifact(name: String = "Luke") = MantraArtifact(
        id = entityId,
        publicKey = key,
        name = name,
        url = "https://example.invalid/luke",
        visibility = "public",
        dialectId = "d".repeat(64),
        license = "CC0",
        chatRoomId = room,
        signature = "",
    )

    private fun translation(name: String = "Setswana Luke") = MantraTranslationArtifactVersion(
        id = entityId,
        publicKey = key,
        artifactVersionId = "v".repeat(64),
        dialectId = "d".repeat(64),
        name = name,
        visibility = "public",
        license = "CC0",
        signature = "",
        chatRoomId = room,
    )

    private fun project(name: String = "Southern Africa") = MantraProject(
        id = entityId,
        publicKey = key,
        name = name,
        description = "Four dialects, one gospel.",
        creatorPublicKey = key,
        ownerPublicKeys = key,
        chatRoomId = room,
        signature = "",
    )

    private fun assertCandidate(entity: SubgroupOriginEntity, kind: Int, name: String) {
        val candidate = SubgroupOrigin.of(entity)

        assertEquals(room, candidate.parentChatRoomId)
        assertEquals(name, candidate.name)
        assertEquals(SubgroupOrigin.Ref(kind = kind, id = entityId), candidate.origin)
    }

    @Test
    fun `a dialect names its room, its name and kind 30304`() {
        assertCandidate(dialect(), DialectEvent.KIND, "Sesotho")
    }

    @Test
    fun `an artifact names its room, its name and kind 30300`() {
        assertCandidate(artifact(), ArtifactEvent.KIND, "Luke")
    }

    @Test
    fun `a translation names its room, its name and kind 30306`() {
        assertCandidate(translation(), TranslationArtifactVersionEvent.KIND, "Setswana Luke")
    }

    @Test
    fun `a project names its room, its name and kind 30330`() {
        assertCandidate(project(), ProjectEvent.KIND, "Southern Africa")
    }

    /**
     * The four kinds are four, which is the whole reason the ref carries one. An
     * id alone would have to be looked up in four tables, and this is the case
     * that fails if somebody gives two entities the same kind by copying a
     * sibling.
     */
    @Test
    fun `the four kinds are distinct`() {
        val kinds = listOf(dialect(), artifact(), translation(), project())
            .map { SubgroupOrigin.of(it).origin.kind }

        assertEquals(4, kinds.distinct().size, "two entities report the same kind: $kinds")
    }

    /**
     * Bare, with no `#`. `MarmotGroupName.of` adds it where the name is used --
     * into the certificate the parent signs, and onto the room -- and it is
     * idempotent, so a name normalised here as well would still come out right.
     * It is kept out anyway because this name goes into a text field the user is
     * about to type in, and the `#` is drawn beside that field rather than put
     * inside it.
     */
    @Test
    fun `the name carries no group prefix`() {
        assertEquals("Sesotho", SubgroupOrigin.of(dialect()).name)
        assertEquals("#Sesotho", MarmotGroupName.of(SubgroupOrigin.of(dialect()).name))
    }

    @Test
    fun `surrounding space is trimmed`() {
        assertEquals("Sesotho", SubgroupOrigin.of(dialect("  Sesotho  ")).name)
    }

    /**
     * A blank name stays blank rather than becoming "a group" or the entity's id.
     * The picker refuses to confirm without one -- "Give the subgroup a name." --
     * and inventing a name here would route around a refusal whose whole job is
     * to be hit.
     */
    @Test
    fun `a blank name is handed back blank, not invented`() {
        assertEquals("", SubgroupOrigin.of(dialect("   ")).name)
        assertEquals("", SubgroupOrigin.of(project("")).name)
    }

    /**
     * The parent is the room, not the entity. Easy to get backwards -- both are
     * 64-hex strings on the same row -- and getting it backwards would open a
     * ceremony in a room that does not exist, which fails late and unhelpfully.
     */
    @Test
    fun `the parent is the room the entity lives in`() {
        val candidate = SubgroupOrigin.of(artifact())

        assertEquals(room, candidate.parentChatRoomId)
        assertEquals(entityId, candidate.origin.id)
    }
}
