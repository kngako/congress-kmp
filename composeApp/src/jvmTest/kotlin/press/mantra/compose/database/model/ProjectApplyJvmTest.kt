package press.mantra.compose.database.model

import androidx.room3.Room
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip31Alts.AltTag
import kotlinx.coroutines.runBlocking
import press.mantra.compose.database.MantraDatabase
import press.mantra.compose.database.builder.getRoomDatabase
import press.mantra.compose.nostr.nip30303.ProjectArtifactVersionEvent
import press.mantra.compose.nostr.nip30303.ProjectEvent
import press.mantra.compose.nostr.nip30303.ProjectTranslationArtifactVersionEvent
import press.mantra.compose.nostr.nip30303.tags.ContributorTag
import press.mantra.compose.nostr.nip30303.tags.CreatorTag
import press.mantra.compose.nostr.nip30303.tags.NameTag
import press.mantra.compose.nostr.nip30303.tags.ProjectIdTag
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Instant

/**
 * What a signed project turns into, and the three ways it is allowed not to.
 *
 * The apply arm is the only place a project row is written -- `FrostSigningManager`
 * reuses this dispatch rather than repeating it -- so it is also the only place
 * the event's claims about people become columns. Two of those claims are
 * required and the arm refuses the event rather than inventing them, which
 * matters more than it looks: the whole reason the creator and the owners are
 * tags is that they are inside the id the quorum signed, and a row that could
 * have been filled in from local knowledge would be worth less than no row.
 *
 * The third refusal is the owners disappearing while the event stays valid.
 * That is what `ContributorTag.ROLE_OWNER` missing from `KNOWN_ROLES` looks
 * like from here -- the role parses as null and `owners()` comes back empty --
 * and the only thing standing between that and a silent regression is this
 * test, since nothing logs and the signature still verifies.
 */
class ProjectApplyJvmTest {

    private val db: MantraDatabase = getRoomDatabase(
        Room.inMemoryDatabaseBuilder<MantraDatabase>()
    )

    @AfterTest
    fun closeDb() = db.close()

    private val roomId = "b".repeat(64)
    private val signature = "0".repeat(128)

    private val creator = "a".repeat(64)
    private val ownerOne = "1".repeat(64)
    private val ownerTwo = "2".repeat(64)
    private val ownerThree = "4".repeat(64)

    private val artifactVersionId = "3".repeat(64)
    private val translationId = "5".repeat(64)

    private suspend fun seed() {
        val nostrEventId = "e".repeat(64)
        db.nostrEventDao().upsert(
            NostrEvent(
                id = nostrEventId,
                pubKey = creator,
                kind = 0,
                tags = emptyArray(),
                content = "{}",
                sig = signature,
            )
        )
        db.profileDao().upsert(Profile(publicKey = creator, nostrEventId = nostrEventId))
        db.chatRoomDao().upsert(
            ChatRoom(
                id = roomId,
                userPublicKey = creator,
                subject = "a translation room",
                description = null,
                mlsGroupState = null,
            )
        )

        val dialectId = "1".repeat(64)
        db.mantraDialectDao().upsert(
            MantraDialect(
                id = dialectId,
                publicKey = roomId,
                name = "Sesotho",
                country = "ZA",
                language = "st",
                signature = signature,
                chatRoomId = roomId,
            )
        )

        val artifactId = "2".repeat(64)
        db.mantraArtifactDao().upsert(
            MantraArtifact(
                id = artifactId,
                publicKey = roomId,
                name = "To Kill a Mockingbird",
                url = "https://harper.com/2-kill-Bird",
                visibility = "private",
                dialectId = dialectId,
                license = "cc",
                chatRoomId = roomId,
                signature = signature,
            )
        )

        db.mantraArtifactVersionDao().upsert(
            MantraArtifactVersion(
                id = artifactVersionId,
                artifactId = artifactId,
                publicKey = roomId,
                versionLabel = "1.0",
                signature = signature,
                chatRoomId = roomId,
            )
        )

        db.mantraTranslationArtifactVersionDao().upsert(
            MantraTranslationArtifactVersion(
                id = translationId,
                publicKey = roomId,
                artifactVersionId = artifactVersionId,
                dialectId = dialectId,
                name = "Sesotho",
                visibility = "private",
                license = "cc",
                signature = signature,
                chatRoomId = roomId,
            )
        )
    }

    /** The event as the group signed it: authored by the room, id over its tags. */
    private fun signed(template: EventTemplate<*>): Event = Event(
        id = EventHasher.hashId(
            pubKey = roomId,
            createdAt = template.createdAt,
            kind = template.kind,
            tags = template.tags,
            content = template.content,
        ),
        pubKey = roomId,
        createdAt = template.createdAt,
        kind = template.kind,
        tags = template.tags,
        content = template.content,
        sig = signature,
    )

    private suspend fun apply(event: Event) = ChatMessage.applyInnerEvent(
        database = db,
        groupId = roomId,
        event = event,
        marmotGroupEventId = null,
        marmotInnerEventId = null,
        senderPublicKey = roomId,
        isUserMessage = false,
        createdAt = Instant.fromEpochSeconds(event.createdAt),
    )

    private fun projectTemplate(owners: Set<String> = setOf(ownerOne)) = ProjectEvent.build(
        name = "Gospel of John into Sesotho",
        description = "The four chapters.",
        creatorPublicKey = creator,
        ownerPublicKeys = owners,
        createdAt = 1_000,
    )

    // --- the project ---

    @Test
    fun `a signed project becomes a row carrying its creator and every owner`() = runBlocking {
        seed()
        val project = signed(projectTemplate(setOf(ownerThree, ownerOne, ownerTwo)))

        val line = apply(project) ?: fail("a project writes a chat line")

        val stored = db.mantraProjectDao().getProjectById(project.id)
            ?: fail("the project was not stored")

        assertEquals("Gospel of John into Sesotho", stored.name)
        assertEquals("The four chapters.", stored.description)
        assertEquals(creator, stored.creatorPublicKey)
        assertEquals(setOf(ownerOne, ownerTwo, ownerThree), stored.ownerPublicKeySet())
        // The room authored it; the creator is a claim it carries, not its author.
        assertEquals(roomId, stored.publicKey)

        assertEquals("project", line.messageType)
        assertTrue(line.content.contains("Gospel of John into Sesotho"))
    }

    @Test
    fun `a project with no creator tag writes nothing`() = runBlocking {
        seed()

        // Everything a project has except the creator, with the tag order the
        // builder uses so this is a plausible event rather than a mangled one.
        val template = ProjectEvent.build(
            name = "Gospel of John into Sesotho",
            description = "The four chapters.",
            creatorPublicKey = creator,
            ownerPublicKeys = setOf(ownerOne),
            createdAt = 1_000,
        )
        val withoutCreator = Event(
            id = "c".repeat(64),
            pubKey = roomId,
            createdAt = template.createdAt,
            kind = template.kind,
            tags = template.tags.filterNot { it.firstOrNull() == CreatorTag.TAG_NAME }
                .toTypedArray(),
            content = template.content,
            sig = signature,
        )

        assertNull(apply(withoutCreator))
        assertNull(db.mantraProjectDao().getProjectById(withoutCreator.id))
    }

    /**
     * The regression this file exists for.
     *
     * An unregistered role is dropped by `ContributorTag.parse`, so the owner
     * tags are still there, the event still verifies, and `owners()` returns
     * nothing. Built by hand with a role that is deliberately not in
     * `KNOWN_ROLES`, because the point is to reproduce what `ROLE_OWNER` would
     * look like if somebody removed it.
     */
    @Test
    fun `a project whose owner role is not a known one writes nothing`() = runBlocking {
        seed()

        val tags = TagArrayBuilder<ProjectEvent>()
            .addUnique(AltTag.assemble(ProjectEvent.ALT_DESCRIPTION))
            .addUnique(NameTag.assemble("Gospel of John into Sesotho"))
            .addUnique(CreatorTag.assemble(creator))
            .addUniqueValueIfNew(ContributorTag.assemble(ownerOne, "proprietor"))
            .build()

        val event = Event(
            id = "d".repeat(64),
            pubKey = roomId,
            createdAt = 1_000,
            kind = ProjectEvent.KIND,
            tags = tags,
            content = "The four chapters.",
            sig = signature,
        )

        assertNull(apply(event))
        assertNull(db.mantraProjectDao().getProjectById(event.id))
    }

    // --- the memberships ---

    @Test
    fun `memberships are stored and write no line of their own`() = runBlocking {
        seed()
        val project = signed(projectTemplate())
        apply(project)

        val source = signed(
            ProjectArtifactVersionEvent.membershipsOf(project, listOf(artifactVersionId)).single()
        )
        val translation = signed(
            ProjectTranslationArtifactVersionEvent
                .membershipsOf(project, listOf(translationId))
                .single()
        )

        // Null, the way a version writes none beside its artifact: the project's
        // own line has already said what happened.
        assertNull(apply(source))
        assertNull(apply(translation))

        assertEquals(
            listOf("1.0"),
            db.mantraProjectArtifactVersionDao()
                .getArtifactVersionsByProjectId(project.id)
                .map { it.versionLabel }
        )
        assertEquals(
            listOf("Sesotho"),
            db.mantraProjectTranslationArtifactVersionDao()
                .getTranslationArtifactVersionsByProjectId(project.id)
                .map { it.name }
        )
    }

    @Test
    fun `a membership naming a version this device lacks leaves the project standing`() =
        runBlocking {
            seed()
            val project = signed(projectTemplate())
            apply(project)

            val absent = "9".repeat(64)
            val source = signed(
                ProjectArtifactVersionEvent.membershipsOf(project, listOf(absent)).single()
            )

            // The foreign key refuses it. What must not happen is the refusal
            // escaping: `applySignedEvent` would report a session that succeeded
            // as one that failed, and the group would be told to abandon a
            // ceremony whose signature is perfectly good.
            assertNull(apply(source))

            assertTrue(
                db.mantraProjectArtifactVersionDao().getByProjectId(project.id).isEmpty()
            )
            assertEquals(
                "Gospel of John into Sesotho",
                db.mantraProjectDao().getProjectById(project.id)?.name
            )
        }

    @Test
    fun `a membership naming a project this device lacks is refused the same way`() =
        runBlocking {
            seed()
            val absentProject = "8".repeat(64)

            val template = ProjectArtifactVersionEvent.build(
                projectId = absentProject,
                artifactVersionId = artifactVersionId,
                createdAt = 1_000,
            )

            assertNull(apply(signed(template)))
            assertTrue(
                db.mantraProjectArtifactVersionDao().getByProjectId(absentProject).isEmpty()
            )
        }

    @Test
    fun `a membership missing the project it belongs to writes nothing`() = runBlocking {
        seed()
        val project = signed(projectTemplate())
        apply(project)

        val template = ProjectArtifactVersionEvent.build(
            projectId = project.id,
            artifactVersionId = artifactVersionId,
            createdAt = 1_000,
        )
        val withoutProject = Event(
            id = "7".repeat(64),
            pubKey = roomId,
            createdAt = template.createdAt,
            kind = template.kind,
            tags = template.tags.filterNot { it.firstOrNull() == ProjectIdTag.TAG_NAME }
                .toTypedArray(),
            content = template.content,
            sig = signature,
        )

        assertNull(apply(withoutProject))
        assertNull(db.mantraProjectArtifactVersionDao().getById(withoutProject.id))
    }
}
