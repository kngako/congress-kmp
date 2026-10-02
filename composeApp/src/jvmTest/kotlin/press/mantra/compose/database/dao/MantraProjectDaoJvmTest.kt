package press.mantra.compose.database.dao

import androidx.room3.Room
import kotlinx.coroutines.runBlocking
import press.mantra.compose.database.MantraDatabase
import press.mantra.compose.database.builder.getRoomDatabase
import press.mantra.compose.database.model.ChatRoom
import press.mantra.compose.database.model.MantraArtifact
import press.mantra.compose.database.model.MantraArtifactVersion
import press.mantra.compose.database.model.MantraDialect
import press.mantra.compose.database.model.MantraProject
import press.mantra.compose.database.model.MantraProjectArtifactVersion
import press.mantra.compose.database.model.MantraProjectTranslationArtifactVersion
import press.mantra.compose.database.model.MantraTranslationArtifactVersion
import press.mantra.compose.database.model.NostrEvent
import press.mantra.compose.database.model.Profile
import press.mantra.compose.nostr.nip30303.ProjectEvent
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The three tables, and the one guarantee the schema makes about them.
 *
 * A project's contents are foreign keys into this room's own rows. That is the
 * scope decision -- one room, for now -- and it is load-bearing rather than
 * decorative: it is what makes a membership naming a version this device does
 * not hold fail at insert, loudly, instead of landing as a row pointing at
 * nothing that every later read has to be defensive about. When projects learn
 * to span rooms the keys go away, and this test is what has to be rewritten
 * rather than quietly starting to pass for a different reason.
 *
 * The other thing here is that the owners are a string. A set stored as text is
 * a shape that rots -- an empty entry, a stray space, the order changing between
 * two devices -- so `ownerPublicKeySet` is tested against each of those rather
 * than against the happy path it obviously handles.
 */
class MantraProjectDaoJvmTest {

    private val db: MantraDatabase = getRoomDatabase(
        Room.inMemoryDatabaseBuilder<MantraDatabase>()
    )

    @AfterTest
    fun closeDb() = db.close()

    private val roomId = "b".repeat(64)
    private val otherRoomId = "f".repeat(64)
    private val signature = "0".repeat(128)

    private val creator = "a".repeat(64)
    private val ownerOne = "1".repeat(64)
    private val ownerTwo = "2".repeat(64)

    private val artifactVersionId = "3".repeat(64)
    private val translationId = "5".repeat(64)

    /** The room, an artifact version and a translation: the keys in the way. */
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

        listOf(roomId, otherRoomId).forEach { id ->
            db.chatRoomDao().upsert(
                ChatRoom(
                    id = id,
                    userPublicKey = creator,
                    subject = "a translation room",
                    description = null,
                    mlsGroupState = null,
                )
            )
        }

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

    private suspend fun project(
        id: String,
        name: String = "Gospel of John",
        chatRoomId: String = roomId,
        owners: String = ownerOne,
    ) = MantraProject(
        id = id,
        publicKey = chatRoomId,
        name = name,
        description = "The four chapters.",
        creatorPublicKey = creator,
        ownerPublicKeys = owners,
        chatRoomId = chatRoomId,
        signature = signature,
    ).also { db.mantraProjectDao().upsert(it) }

    // --- the project table ---

    @Test
    fun `a project round trips, owners and creator intact`() = runBlocking {
        seed()
        project(id = "p".repeat(64), owners = "$ownerOne,$ownerTwo")

        val stored = db.mantraProjectDao().getProjectById("p".repeat(64))
            ?: fail("the project was not stored")

        assertEquals(creator, stored.creatorPublicKey)
        assertEquals(setOf(ownerOne, ownerTwo), stored.ownerPublicKeySet())
        assertTrue(stored.isOwnedBy(ownerTwo))
        assertTrue(!stored.isOwnedBy(creator))
    }

    @Test
    fun `projects are listed per room, by name`() = runBlocking {
        seed()
        project(id = "p2".padEnd(64, '0'), name = "Zechariah")
        project(id = "p1".padEnd(64, '0'), name = "Amos")
        project(id = "p3".padEnd(64, '0'), name = "Elsewhere", chatRoomId = otherRoomId)

        val names = db.mantraProjectDao().getProjectsByChatRoomId(roomId).map { it.name }

        assertEquals(listOf("Amos", "Zechariah"), names)
    }

    // --- ownerPublicKeySet, against the ways a delimited string goes wrong ---

    @Test
    fun `the owner set survives spacing, empties and a single entry`() {
        fun owners(stored: String) = MantraProject(
            id = "p".repeat(64),
            publicKey = roomId,
            name = "n",
            description = "",
            creatorPublicKey = creator,
            ownerPublicKeys = stored,
            chatRoomId = roomId,
            signature = signature,
        ).ownerPublicKeySet()

        assertEquals(setOf(ownerOne), owners(ownerOne))
        assertEquals(setOf(ownerOne, ownerTwo), owners(" $ownerOne , $ownerTwo "))
        assertEquals(setOf(ownerOne), owners("$ownerOne,,"))
        assertEquals(emptySet(), owners(""))
    }

    @Test
    fun `two rows built from the same owners in different orders agree`() {
        val event = { owners: Set<String> ->
            ProjectEvent.build(
                name = "Gospel of John",
                description = "",
                creatorPublicKey = creator,
                ownerPublicKeys = owners,
                createdAt = 1_000,
            )
        }

        val one = MantraProject.fromProjectEventTemplate(
            event(linkedSetOf(ownerTwo, ownerOne)), roomId, roomId
        )
        val other = MantraProject.fromProjectEventTemplate(
            event(linkedSetOf(ownerOne, ownerTwo)), roomId, roomId
        )

        assertEquals(one?.ownerPublicKeys, other?.ownerPublicKeys)
        // And the ids too, since the event sorts before hashing.
        assertEquals(one?.id, other?.id)
    }

    // --- the memberships, and the keys that make a bad one fail ---

    @Test
    fun `a project's contents come back resolved`() = runBlocking {
        seed()
        val projectId = "p".repeat(64)
        project(id = projectId)

        db.mantraProjectArtifactVersionDao().upsert(
            MantraProjectArtifactVersion(
                id = "m1".padEnd(64, '0'),
                publicKey = roomId,
                projectId = projectId,
                artifactVersionId = artifactVersionId,
                chatRoomId = roomId,
                signature = signature,
            )
        )
        db.mantraProjectTranslationArtifactVersionDao().upsert(
            MantraProjectTranslationArtifactVersion(
                id = "m2".padEnd(64, '0'),
                publicKey = roomId,
                projectId = projectId,
                translationArtifactVersionId = translationId,
                chatRoomId = roomId,
                signature = signature,
            )
        )

        val sources = db.mantraProjectArtifactVersionDao()
            .getArtifactVersionsByProjectId(projectId)
        val translations = db.mantraProjectTranslationArtifactVersionDao()
            .getTranslationArtifactVersionsByProjectId(projectId)

        assertEquals(listOf("1.0"), sources.map { it.versionLabel })
        assertEquals(listOf("Sesotho"), translations.map { it.name })
    }

    @Test
    fun `an artifact version says which projects it is in`() = runBlocking {
        seed()
        val projectId = "p".repeat(64)
        project(id = projectId, name = "Gospel of John")

        db.mantraProjectArtifactVersionDao().upsert(
            MantraProjectArtifactVersion(
                id = "m1".padEnd(64, '0'),
                publicKey = roomId,
                projectId = projectId,
                artifactVersionId = artifactVersionId,
                chatRoomId = roomId,
                signature = signature,
            )
        )

        assertEquals(
            listOf("Gospel of John"),
            db.mantraProjectArtifactVersionDao()
                .getProjectsByArtifactVersionId(artifactVersionId)
                .map { it.name }
        )
    }

    @Test
    fun `a membership naming a version this room does not hold is refused`() = runBlocking {
        seed()
        val projectId = "p".repeat(64)
        project(id = projectId)

        val absent = "9".repeat(64)

        val failure = runCatching {
            db.mantraProjectArtifactVersionDao().upsert(
                MantraProjectArtifactVersion(
                    id = "m1".padEnd(64, '0'),
                    publicKey = roomId,
                    projectId = projectId,
                    artifactVersionId = absent,
                    chatRoomId = roomId,
                    signature = signature,
                )
            )
        }.exceptionOrNull()

        // The foreign key, not a check in the apply arm. When Phase 9 takes it
        // away this is the assertion that has to be rewritten rather than left
        // to start passing because nothing writes memberships any more.
        assertTrue(
            failure != null,
            "a membership pointing at an absent version was accepted"
        )
        assertNull(db.mantraProjectArtifactVersionDao().getById("m1".padEnd(64, '0')))
    }

    @Test
    fun `deleting the room takes the project and its memberships with it`() = runBlocking {
        seed()
        val projectId = "p".repeat(64)
        project(id = projectId)

        db.mantraProjectArtifactVersionDao().upsert(
            MantraProjectArtifactVersion(
                id = "m1".padEnd(64, '0'),
                publicKey = roomId,
                projectId = projectId,
                artifactVersionId = artifactVersionId,
                chatRoomId = roomId,
                signature = signature,
            )
        )

        db.chatRoomDao().delete(
            ChatRoom(
                id = roomId,
                userPublicKey = creator,
                subject = "a translation room",
                description = null,
                mlsGroupState = null,
            )
        )

        assertNull(db.mantraProjectDao().getProjectById(projectId))
        assertTrue(db.mantraProjectArtifactVersionDao().getByProjectId(projectId).isEmpty())
    }
}
