package press.mantra.compose.managers

import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import com.vitorpamplona.quartz.nip01Core.crypto.Nip01Crypto
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.PrivateKey
import fr.acinq.bitcoin.crypto.frost.Frost
import fr.acinq.bitcoin.crypto.frost.IndividualNonce
import fr.acinq.bitcoin.crypto.frost.KeyMaterial
import fr.acinq.bitcoin.crypto.frost.SecretNonce
import fr.acinq.bitcoin.crypto.frost.Session
import fr.acinq.bitcoin.crypto.frost.TweakCache
import fr.acinq.secp256k1.Hex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import press.mantra.compose.database.model.DkgSession
import press.mantra.compose.database.model.FrostSigningSession
import press.mantra.compose.extensions.toHex
import press.mantra.compose.nostr.frost.FrostSigningEvents

/**
 * The two rounds a signing session runs, against real FROST.
 *
 * `FrostSigningManager` spreads these steps across arriving messages, several
 * devices and a database, none of which a unit test can stand up. What it can
 * do is run the same calls in the same order with the same arguments and check
 * that what comes out is a signature nostr will accept — which is the part
 * that was written from reading the library rather than from a working example,
 * and so the part most likely to be subtly wrong.
 *
 * A signature that verifies is the whole contract: if these calls are wired up
 * incorrectly the aggregate simply fails to verify, silently, on every device.
 */
class FrostSigningRoundTest {
    private val participants = 3
    private val threshold = 2

    /** Stands in for a completed ceremony. A trusted dealer is fine here: the test is about signing. */
    private val keyMaterial: KeyMaterial = Frost.trustedDealerKeygen(
        thresholdSecretKey = PrivateKey(
            ByteVector32("1c0ffee0000000000000000000000000000000000000000000000000000000a1")
        ),
        nParticipants = participants,
        threshold = threshold
    )

    private val tweakCache: TweakCache = TweakCache.create(keyMaterial.thresholdPublicKey)

    /** The group's nostr identity: the x-only key a BIP-340 signature verifies against. */
    private val groupPubKey = tweakCache.tweakedPublicKey.value.toHex()

    /** The 32 bytes actually signed — a nostr event id, exactly as the manager computes it. */
    private fun eventId(content: String): String = EventHasher.hashId(
        pubKey = groupPubKey,
        createdAt = 1_700_000_000L,
        kind = 1,
        tags = arrayOf(),
        content = content
    )

    /**
     * One signer's half of the protocol, in the manager's order: regenerate the
     * nonce from stored randomness, then sign once the set is known.
     */
    private fun nonceOf(signerId: Int, message: ByteVector, random: String): Pair<SecretNonce, IndividualNonce> =
        SecretNonce.generate(
            sessionRandom = ByteVector32(random),
            secretShare = keyMaterial.secretShares[signerId],
            publicShare = keyMaterial.publicShares[signerId],
            tweakedThresholdPublicKey = tweakCache.tweakedPublicKey,
            message = message,
            extraInput = null
        )

    private fun sessionFor(signerIds: List<Int>, nonces: List<IndividualNonce>, message: ByteVector): Session {
        val aggregated = IndividualNonce.aggregate(nonces).right!!

        return Session.create(
            aggregatedNonce = aggregated,
            signerIds = signerIds.map { it.toUInt() },
            signerPublicShares = signerIds.map { keyMaterial.publicShares[it] },
            nParticipants = participants,
            threshold = threshold,
            tweakCache = tweakCache,
            message = message
        )
    }

    @Test
    fun `a threshold of signers produces a signature nostr accepts`() {
        val id = eventId("the group agrees")
        val message = ByteVector(id.hexToByteArray())

        // Two of the three sign, which is the point of a 2-of-3 key.
        val signerIds = listOf(0, 1)
        val nonces = signerIds.map { nonceOf(it, message, "a".repeat(63) + "${it + 1}") }

        val session = sessionFor(signerIds, nonces.map { it.second }, message)

        val partials = signerIds.mapIndexed { position, signerId ->
            session.sign(nonces[position].first, keyMaterial.secretShares[signerId], signerId.toUInt()).right!!
        }

        val signature = session.aggregateSigs(partials).right!!

        assertTrue(
            Nip01Crypto.verify(
                signature = signature.toByteArray(),
                hash = id.hexToByteArray(),
                pubKey = groupPubKey.hexToByteArray()
            ),
            "the aggregated signature must verify against the group's x-only key"
        )
    }

    @Test
    fun `a different pair of signers signs the same event just as well`() {
        val id = eventId("the group agrees")
        val message = ByteVector(id.hexToByteArray())

        // Whoever happens to be available. The coordinator picks; the signature
        // that comes out must not depend on which t it picked.
        val signerIds = listOf(1, 2)
        val nonces = signerIds.map { nonceOf(it, message, "b".repeat(63) + "${it + 1}") }

        val session = sessionFor(signerIds, nonces.map { it.second }, message)
        val partials = signerIds.mapIndexed { position, signerId ->
            session.sign(nonces[position].first, keyMaterial.secretShares[signerId], signerId.toUInt()).right!!
        }

        val signature = session.aggregateSigs(partials).right!!

        assertTrue(
            Nip01Crypto.verify(
                signature = signature.toByteArray(),
                hash = id.hexToByteArray(),
                pubKey = groupPubKey.hexToByteArray()
            )
        )
    }

    @Test
    fun `a signature over one event does not verify against another`() {
        val id = eventId("the group agrees")
        val message = ByteVector(id.hexToByteArray())

        val signerIds = listOf(0, 1)
        val nonces = signerIds.map { nonceOf(it, message, "c".repeat(63) + "${it + 1}") }
        val session = sessionFor(signerIds, nonces.map { it.second }, message)
        val partials = signerIds.mapIndexed { position, signerId ->
            session.sign(nonces[position].first, keyMaterial.secretShares[signerId], signerId.toUInt()).right!!
        }
        val signature = session.aggregateSigs(partials).right!!

        assertFalse(
            Nip01Crypto.verify(
                signature = signature.toByteArray(),
                hash = eventId("the group agrees to something else").hexToByteArray(),
                pubKey = groupPubKey.hexToByteArray()
            ),
            "a signature is over one event id and must not carry to another"
        )
    }

    @Test
    fun `regenerating a nonce from the same seed and message gives the same nonce`() {
        val id = eventId("the group agrees")
        val message = ByteVector(id.hexToByteArray())
        val random = "d".repeat(63) + "1"

        // What makes a signing session restart-safe: SecretNonce cannot be stored,
        // so the manager keeps its seed and derives again. If that were not
        // reproducible a device that restarted mid-session would publish a partial
        // signature against a nonce nobody aggregated.
        val first = nonceOf(0, message, random).second
        val second = nonceOf(0, message, random).second

        assertEquals(first.data.toHex(), second.data.toHex())
    }

    @Test
    fun `the same seed under a different message gives a different nonce`() {
        val random = "e".repeat(63) + "1"

        // The safety property behind reusing the seed at all: one session signs one
        // message. Were the nonce independent of the message, a session that could
        // be re-pointed at another event would sign twice under one nonce, which
        // hands over the secret share.
        val first = nonceOf(0, ByteVector(eventId("one thing").hexToByteArray()), random).second
        val second = nonceOf(0, ByteVector(eventId("another thing").hexToByteArray()), random).second

        assertFalse(first.data.toHex() == second.data.toHex())
    }
}

/**
 * The pure bits of a signing session's bookkeeping: who is signing, and with
 * which key.
 */
class FrostSigningSessionTest {
    private fun session(signerId: Int, signerIds: String?) = FrostSigningSession(
        id = "s".repeat(64),
        chatRoomId = "room",
        coordinatorPublicKey = "c".repeat(64),
        userPublicKey = "u".repeat(64),
        dkgSessionId = "k".repeat(64),
        threshold = 2,
        participantCount = 3,
        signerId = signerId,
        unsignedEventJson = "{}",
        eventId = "e".repeat(64),
        nonceRandom = "f".repeat(64),
        signerIds = signerIds
    )

    @Test
    fun `a member left out of the signer set is not a signer`() {
        assertTrue(session(signerId = 1, signerIds = "0,1").isSigner())
        assertFalse(session(signerId = 2, signerIds = "0,1").isSigner())
    }

    @Test
    fun `nobody is a signer until the coordinator has chosen`() {
        assertFalse(session(signerId = 0, signerIds = null).isSigner())
    }

    @Test
    fun `the signer set keeps the order it was aggregated in`() {
        // FROST binds the set into the challenge, so this list is not a set of ids
        // but a sequence positionally matched to the aggregated nonce.
        assertEquals(listOf(2, 0, 1), session(signerId = 0, signerIds = "2,0,1").signerIdList())
    }

    @Test
    fun `a signer set tag survives the trip through a tag array`() {
        val tags = FrostSigningEvents.assembleTags(
            sessionId = "session",
            dkgSessionId = "ceremony",
            signerIds = listOf(2, 0, 1)
        )

        assertEquals("session", FrostSigningEvents.parseSessionId(tags))
        assertEquals("ceremony", FrostSigningEvents.parseKey(tags))
        assertEquals(listOf(2, 0, 1), FrostSigningEvents.parseSignerIds(tags))
    }

    @Test
    fun `a ceremony that recorded no public shares reads back null rather than empty`() {
        // Ceremonies completed before the column existed. Signing falls back to not
        // cross-checking shares, which the FROST API allows, rather than refusing.
        val ceremony = DkgSession(
            id = "k".repeat(64),
            chatRoomId = "room",
            coordinatorPublicKey = "c".repeat(64),
            userPublicKey = "u".repeat(64),
            threshold = 2,
            participantCount = 3,
            hostPublicKey = "h".repeat(66),
            round1Random = "1".repeat(64),
            round2AuxRandom = "2".repeat(64)
        )

        assertEquals(null, ceremony.publicShareList())
        assertEquals(
            2,
            ceremony.copy(
                publicShares = listOf(
                    Hex.encode(ByteArray(33) { 2 }),
                    Hex.encode(ByteArray(33) { 3 })
                ).joinToString(",")
            ).publicShareList()?.size
        )
    }
}
