package press.mantra.compose.managers

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import com.vitorpamplona.quartz.nip01Core.crypto.Nip01Crypto
import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.PrivateKey
import fr.acinq.bitcoin.crypto.frost.Frost
import fr.acinq.bitcoin.crypto.frost.IndividualNonce
import fr.acinq.bitcoin.crypto.frost.KeyMaterial
import fr.acinq.bitcoin.crypto.frost.SecretNonce
import fr.acinq.bitcoin.crypto.frost.Session
import fr.acinq.bitcoin.crypto.frost.TweakCache
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import press.mantra.compose.database.model.FrostSigningItem
import press.mantra.compose.database.model.MantraArtifact
import press.mantra.compose.database.model.MantraArtifactVersion
import press.mantra.compose.extensions.toHex
import press.mantra.compose.nostr.nip30303.ArtifactEvent

/**
 * An artifact the group signed, from proposal to rows, against real FROST.
 *
 * Adding an artifact used to write a row and submit it; the row said the
 * submitter wrote it, because they had. Now the group signs it, and the claim
 * this test exists to hold is that the artifact every device ends up with is
 * the group's: authored by the threshold key, carrying a signature that
 * verifies, with an id every member arrives at independently.
 *
 * None of that is visible when it breaks. A row whose author is the proposer
 * looks exactly like a row whose author is the group -- both are opaque hex --
 * and a group that disagrees about the id has two artifacts that look like one.
 */
class SignedArtifactTest {
    private val participants = 3
    private val threshold = 2

    /** The member who filled in the form. Nothing they own should end up on the row. */
    private val proposer = "9".repeat(64)
    private val dialectId = "b".repeat(64)

    /** Stands in for a completed ceremony; the test is about what gets signed, not the DKG. */
    private val keyMaterial: KeyMaterial = Frost.trustedDealerKeygen(
        thresholdSecretKey = PrivateKey(
            ByteVector32("1c0ffee0000000000000000000000000000000000000000000000000000000a1")
        ),
        nParticipants = participants,
        threshold = threshold
    )

    /**
     * The room the group signs in: derived from its key at the admin path, which
     * is what makes the room's id and the key it signs as one value.
     */
    private val room: SharedKeyDerivation.Derived = SharedKeyDerivation.derive(
        thresholdPublicKey = keyMaterial.thresholdPublicKey.value.toHex(),
        path = SharedKeyDerivation.MARMOT_ADMIN_GROUP_PATH
    )

    private val tweakCache: TweakCache = room.cache

    /** The group's nostr identity here, which is also [chatRoomId]. */
    private val groupPubKey = room.hex

    private val chatRoomId = room.hex

    private fun proposalTemplate(versionLabel: String = "1.0") = ArtifactEvent.build(
        name = "In Detention",
        url = "https://example.com/in-detention",
        visibility = "private",
        license = "cc",
        dialectId = dialectId,
        versionLabel = versionLabel,
        createdAt = 1_700_000_000L,
    )

    /**
     * Exactly what `FrostSigningManager.unsignedEventOf` does, and it must stay
     * exactly that: the proposer's fields re-authored under the group's key.
     */
    private fun unsignedEventOf(template: com.vitorpamplona.quartz.nip01Core.signers.EventTemplate<*>) = Event(
        id = EventHasher.hashId(
            pubKey = groupPubKey,
            createdAt = template.createdAt,
            kind = template.kind,
            tags = template.tags,
            content = template.content
        ),
        pubKey = groupPubKey,
        createdAt = template.createdAt,
        kind = template.kind,
        tags = template.tags,
        content = template.content,
        sig = ""
    )

    private fun itemOver(unsignedEvent: Event) = FrostSigningItem(
        sessionId = "s".repeat(64),
        itemIndex = 0,
        unsignedEventJson = unsignedEvent.toJson(),
        eventId = unsignedEvent.id,
        nonceRandom = "f".repeat(64)
    )

    /** A quorum signing the item's event, in the manager's order. */
    private fun groupSignature(item: FrostSigningItem): String {
        val message = ByteVector(item.eventId.hexToByteArray())
        val signerIds = listOf(0, 1)

        val nonces = signerIds.map { signerId ->
            SecretNonce.generate(
                sessionRandom = ByteVector32("a".repeat(63) + "${signerId + 1}"),
                secretShare = keyMaterial.secretShares[signerId],
                publicShare = keyMaterial.publicShares[signerId],
                tweakedThresholdPublicKey = tweakCache.tweakedPublicKey,
                message = message,
                extraInput = null
            )
        }

        val signingSession = Session.create(
            aggregatedNonce = IndividualNonce.aggregate(nonces.map { it.second }).right!!,
            signerIds = signerIds.map { it.toUInt() },
            signerPublicShares = signerIds.map { keyMaterial.publicShares[it] },
            nParticipants = participants,
            threshold = threshold,
            tweakCache = tweakCache,
            message = message
        )

        val partials = signerIds.mapIndexed { position, signerId ->
            signingSession.sign(
                nonces[position].first,
                keyMaterial.secretShares[signerId],
                signerId.toUInt()
            ).right!!
        }

        return signingSession.aggregateSigs(partials).right!!.toByteArray().toHex()
    }

    /** Everything from the form to the row a device holds afterwards. */
    private fun signedArtifactEvent(versionLabel: String = "1.0"): ArtifactEvent {
        val item = itemOver(unsignedEventOf(proposalTemplate(versionLabel)))
        val signed = FrostSigningManager.signedEvent(item, groupSignature(item))

        return ArtifactEvent(
            signed.id, signed.pubKey, signed.createdAt, signed.tags, signed.content, signed.sig
        )
    }

    @Test
    fun `the artifact the group signs is authored by the group, not the proposer`() {
        val artifact = MantraArtifact.fromArtifactEvent(signedArtifactEvent(), chatRoomId)

        assertNotNull(artifact)
        assertEquals(groupPubKey, artifact.publicKey)
        assertNotEquals(proposer, artifact.publicKey)
    }

    @Test
    fun `the group it is authored by is the room it was signed in`() {
        // Signing runs at the room's derivation path, so the author is the room's
        // own id rather than the bare threshold key. That is what lets anybody
        // holding the row check it without being told which key to expect -- and
        // it is why the path a session signs at cannot come from the proposer.
        val artifact = MantraArtifact.fromArtifactEvent(signedArtifactEvent(), chatRoomId)

        assertEquals(chatRoomId, artifact?.publicKey)
        assertNotEquals(
            keyMaterial.thresholdPublicKey.xOnly().value.toHex(),
            artifact?.publicKey,
            "an artifact must be signed by the room's key, not by the group's root key"
        )
    }

    @Test
    fun `the row's id is the id the group put its signature to`() {
        // Every device builds this row from the same signed event, so the id has
        // to be the one that was signed rather than anything recomputed from the
        // proposer. Otherwise members converge on nothing and each holds its own
        // copy of what is meant to be one artifact.
        val item = itemOver(unsignedEventOf(proposalTemplate()))
        val signed = FrostSigningManager.signedEvent(item, groupSignature(item))

        val artifact = MantraArtifact.fromArtifactEvent(
            ArtifactEvent(signed.id, signed.pubKey, signed.createdAt, signed.tags, signed.content, signed.sig),
            chatRoomId
        )

        assertEquals(item.eventId, artifact?.id)
    }

    @Test
    fun `the signature on the row verifies against the row's own id and author`() {
        // The payoff of signing rather than submitting: the row carries proof the
        // group made it, checkable by anybody holding it.
        val artifact = MantraArtifact.fromArtifactEvent(signedArtifactEvent(), chatRoomId)

        assertNotNull(artifact)
        assertTrue(
            Nip01Crypto.verify(
                signature = artifact.signature.hexToByteArray(),
                hash = artifact.id.hexToByteArray(),
                pubKey = artifact.publicKey.hexToByteArray()
            ),
            "an artifact row should carry a signature the group's key made over its own id"
        )
    }

    @Test
    fun `what the form asked for is what the group signed`() {
        // The fields travel as tags through a session that knows nothing about
        // artifacts. Anything dropped in there is signed away silently.
        val artifact = MantraArtifact.fromArtifactEvent(signedArtifactEvent(), chatRoomId)

        assertEquals("In Detention", artifact?.name)
        assertEquals("https://example.com/in-detention", artifact?.url)
        assertEquals("private", artifact?.visibility)
        assertEquals("cc", artifact?.license)
        assertEquals(dialectId, artifact?.dialectId)
    }

    @Test
    fun `the artifact arrives with the first version hanging off it`() {
        // Nothing sends this row: each device derives it from the artifact it
        // just applied. A chapter attaches to a version rather than to an
        // artifact, so an artifact that arrives without one is inert.
        val signed = signedArtifactEvent(versionLabel = "First Edition")

        val artifact = MantraArtifact.fromArtifactEvent(signed, chatRoomId)
        val version = MantraArtifactVersion.initialVersionOf(signed, chatRoomId)

        assertNotNull(version)
        assertEquals(artifact?.id, version.artifactId)
        assertEquals("First Edition", version.versionLabel)
        assertEquals(groupPubKey, version.publicKey)
        // Derived, not signed: the group signed the artifact that declares it.
        assertEquals("", version.signature)
    }
}
