package press.mantra.compose.database.model

import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import press.mantra.compose.nostr.nip30303.ArtifactEvent
import press.mantra.compose.nostr.nip30303.tags.ArtifactVersionMetadataTag

/**
 * The first version of an artifact is derived, not delivered.
 *
 * The group signs an artifact and nothing else, so the version it starts life
 * with is not an event anybody sent: every device builds the row for itself out
 * of the artifact it already holds. That only works while every device builds
 * the *same* row, and nothing about the ids would show it if they stopped —
 * they are content hashes, opaque hex either way. What would show is a group
 * that quietly disagrees about which version a chapter hangs off, with the
 * artifact looking identical on every screen.
 */
class InitialArtifactVersionTest {
    private val groupKey = "a".repeat(64)
    private val dialectId = "b".repeat(64)
    private val chatRoomId = "room"

    private fun signedArtifact(
        name: String = "In Detention",
        versionLabel: String = "1.0",
        createdAt: Long = 1_700_000_000,
    ): ArtifactEvent {
        val template = ArtifactEvent.build(
            name = name,
            url = "example.com",
            visibility = "private",
            license = "cc",
            dialectId = dialectId,
            versionLabel = versionLabel,
            createdAt = createdAt,
        )

        // Hashed rather than made up, so two fixtures that differ are two
        // different artifacts here for the same reason they would be in the app.
        return ArtifactEvent(
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
    fun `the derived version is a function of the artifact and nothing else`() {
        // Every input has to come off the artifact. Reading the clock here would
        // still agree with itself twice in a row -- and disagree between two
        // devices that applied the same artifact minutes apart, which is the
        // case nobody can reproduce on demand. So the timestamp is checked
        // against the artifact's rather than against a second derivation.
        val artifact = signedArtifact(createdAt = 1_700_000_000)

        val version = MantraArtifactVersion.initialVersionOf(artifact, chatRoomId)

        assertNotNull(version)
        assertEquals(1_700_000_000, version.createdAt.epochSeconds)
    }

    @Test
    fun `two devices derive the same first version from the same artifact`() {
        val artifact = signedArtifact()

        val mine = MantraArtifactVersion.initialVersionOf(artifact, chatRoomId)
        val theirs = MantraArtifactVersion.initialVersionOf(artifact, chatRoomId)

        assertNotNull(mine)
        assertEquals(mine.id, theirs?.id)
        assertEquals(mine.createdAt, theirs?.createdAt)
    }

    @Test
    fun `an artifact signed at a different moment derives a different version`() {
        // The artifact's own timestamp is bound into the derived id, so two
        // proposals identical but for when they were made stay two artifacts
        // with two first versions rather than colliding on one row.
        val first = MantraArtifactVersion.initialVersionOf(signedArtifact(createdAt = 1_700_000_000), chatRoomId)
        val second = MantraArtifactVersion.initialVersionOf(signedArtifact(createdAt = 1_700_000_001), chatRoomId)

        assertNotNull(first)
        assertNotNull(second)
        assertNotEquals(first.id, second.id)
    }

    @Test
    fun `the derived version hangs off the artifact and carries what it declared`() {
        val artifact = signedArtifact(versionLabel = "First Edition")

        val version = MantraArtifactVersion.initialVersionOf(artifact, chatRoomId)

        assertEquals(artifact.id, version?.artifactId)
        assertEquals("First Edition", version?.versionLabel)
        // Authored by whoever authored the artifact -- the group, once signed --
        // and unsigned, because nobody signed this.
        assertEquals(groupKey, version?.publicKey)
        assertEquals("", version?.signature)
    }

    @Test
    fun `the label is bound into the id rather than hung beside it`() {
        // Two artifacts alike but for the label must not derive one version
        // between them: the id has to come from the whole event, or a group
        // renaming a version would leave the row it replaces in place.
        val first = MantraArtifactVersion.initialVersionOf(signedArtifact(versionLabel = "1.0"), chatRoomId)
        val second = MantraArtifactVersion.initialVersionOf(signedArtifact(versionLabel = "2.0"), chatRoomId)

        assertNotNull(first)
        assertNotNull(second)
        assertNotEquals(first.id, second.id)
    }

    @Test
    fun `an artifact that declares no version derives none`() {
        // Artifacts written before the artifact carried its first version.
        val declared = signedArtifact()
        val silent = ArtifactEvent(
            id = declared.id,
            pubKey = declared.pubKey,
            createdAt = declared.createdAt,
            tags = declared.tags.filterNot { it.firstOrNull() == ArtifactVersionMetadataTag.TAG_NAME }
                .toTypedArray(),
            content = declared.content,
            sig = declared.sig
        )

        assertNull(MantraArtifactVersion.initialVersionOf(silent, chatRoomId))
    }
}
