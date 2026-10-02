package press.mantra.compose.nostr.nip30303

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import press.mantra.compose.nostr.nip30303.tags.ContributorTag
import press.mantra.compose.nostr.nip30303.tags.CreatorTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A project names people two different ways, and both ways fail quietly.
 *
 * The creator is a [CreatorTag] and the owners are [ContributorTag]s with
 * [ContributorTag.ROLE_OWNER]. Each has a failure mode that produces a valid,
 * verifiable event carrying nobody:
 *
 *  - a role missing from `ContributorTag`'s known set parses as null, so every
 *    owner vanishes while the role string stays inside the signed id;
 *  - `addUnique` keeps one tag per *name*, so three owners emitted with it
 *    become one, which reads like a bug in whatever collected them.
 *
 * Neither logs anything. These tests are the only thing that would say.
 */
class ProjectEventTest {
    private val creator = "a".repeat(64)
    private val owner1 = "1".repeat(64)
    private val owner2 = "2".repeat(64)
    private val owner3 = "3".repeat(64)

    private fun EventTemplate<ProjectEvent>.asEvent() = ProjectEvent(
        id = "e".repeat(64),
        pubKey = creator,
        createdAt = createdAt,
        tags = tags,
        content = content,
        sig = ""
    )

    private fun project(owners: Set<String>) = ProjectEvent.build(
        name = "Gospel of John into Sesotho",
        description = "The four chapters, with the Setswana back-translation.",
        creatorPublicKey = creator,
        ownerPublicKeys = owners
    ).asEvent()

    // --- the role has to be known, or there are no owners at all ---

    @Test
    fun `the owner role survives a round trip through ContributorTag`() {
        val parsed = ContributorTag.parse(
            ContributorTag.assemble(owner1, ContributorTag.ROLE_OWNER)
        )

        assertEquals(owner1, parsed?.pubKey)
        // If ROLE_OWNER is ever dropped from KNOWN_ROLES this is the only place
        // that says so. Everything downstream just sees a project with no owners.
        assertEquals(ContributorTag.ROLE_OWNER, parsed?.role)
    }

    // --- the creator tag ---

    @Test
    fun `a creator tag round trips`() {
        assertEquals(creator, CreatorTag.parse(CreatorTag.assemble(creator))?.pubKey)
    }

    @Test
    fun `a creator tag whose value is not a key is refused`() {
        assertNull(CreatorTag.parse(arrayOf(CreatorTag.TAG_NAME, "not-a-key")))
        assertNull(CreatorTag.parse(arrayOf(CreatorTag.TAG_NAME)))
        assertNull(CreatorTag.parse(arrayOf("p", creator)))
    }

    // --- owners are a set, and all of them survive ---

    @Test
    fun `three owners come back, sorted`() {
        val event = project(setOf(owner3, owner1, owner2))

        // `addUnique` here would leave one. Sorted because the id is a hash over
        // the tags as emitted and a form hands its selection over in no
        // particular order.
        assertEquals(listOf(owner1, owner2, owner3), event.owners())
    }

    @Test
    fun `an owner listed twice appears once`() {
        assertEquals(listOf(owner1), project(setOf(owner1)).owners())
    }

    @Test
    fun `a project with no owners cannot be built`() {
        assertFailsWith<IllegalArgumentException> {
            ProjectEvent.build(
                name = "Nobody's",
                description = "",
                creatorPublicKey = creator,
                ownerPublicKeys = emptySet()
            )
        }
    }

    @Test
    fun `owners ignores a contributor tag that is not an owner`() {
        val event = ProjectEvent.build(
            name = "Gospel of John into Sesotho",
            description = "",
            creatorPublicKey = creator,
            ownerPublicKeys = setOf(owner1)
        ) {
            addUniqueValueIfNew(
                ContributorTag.assemble(owner2, ContributorTag.ROLE_EDITOR)
            )
        }.asEvent()

        assertEquals(listOf(owner1), event.owners())
    }

    // --- the creator and the owners are different tags, so one can be both ---

    @Test
    fun `the creator may also be an owner`() {
        val event = project(setOf(creator, owner1))

        assertEquals(creator, event.creator())
        assertTrue(creator in event.owners())
        assertTrue(owner1 in event.owners())
    }

    @Test
    fun `the creator is not read off the owner tags`() {
        val event = project(setOf(owner1))

        // Selecting the creator by position among p-tags would make these agree,
        // which reads as working.
        assertEquals(creator, event.creator())
        assertEquals(listOf(owner1), event.owners())
    }

    @Test
    fun `name and description land where they are read from`() {
        val event = project(setOf(owner1))

        assertEquals("Gospel of John into Sesotho", event.name())
        assertTrue(event.content.startsWith("The four chapters"))
    }

    // --- memberships borrow the project's id and timestamp ---

    @Test
    fun `memberships name the project and share its timestamp`() {
        val signedProject: Event = project(setOf(owner1))
        val versionId = "f".repeat(64)

        val sources = ProjectArtifactVersionEvent.membershipsOf(
            project = signedProject,
            artifactVersionIds = listOf(versionId)
        )
        val translations = ProjectTranslationArtifactVersionEvent.membershipsOf(
            project = signedProject,
            translationArtifactVersionIds = listOf(versionId)
        )

        val source = ProjectArtifactVersionEvent(
            id = "0".repeat(64),
            pubKey = creator,
            createdAt = sources.single().createdAt,
            tags = sources.single().tags,
            content = sources.single().content,
            sig = ""
        )

        assertEquals(signedProject.id, source.projectId())
        assertEquals(versionId, source.artifactVersionId())
        assertEquals(signedProject.createdAt, source.createdAt)

        val translation = ProjectTranslationArtifactVersionEvent(
            id = "0".repeat(64),
            pubKey = creator,
            createdAt = translations.single().createdAt,
            tags = translations.single().tags,
            content = translations.single().content,
            sig = ""
        )

        assertEquals(signedProject.id, translation.projectId())
        assertEquals(versionId, translation.translationArtifactVersionId())
        assertEquals(signedProject.createdAt, translation.createdAt)
    }

    @Test
    fun `a project founded with no contents has no memberships`() {
        val signedProject: Event = project(setOf(owner1))

        assertTrue(
            ProjectArtifactVersionEvent.membershipsOf(signedProject, emptyList()).isEmpty()
        )
        assertTrue(
            ProjectTranslationArtifactVersionEvent
                .membershipsOf(signedProject, emptyList())
                .isEmpty()
        )
    }
}
