package press.mantra.compose.managers

import press.mantra.compose.extensions.toHex
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.Crypto
import fr.acinq.bitcoin.PublicKey
import fr.acinq.bitcoin.XonlyPublicKey
import fr.acinq.bitcoin.crypto.frost.TweakCache
import fr.acinq.bitcoin.utils.Either

/**
 * Keys derived from a group's ChillDKG threshold key by FROST tweaks.
 *
 * A derived key here is a real key the group can sign with: each step applies an
 * additive tweak, `K' = K + t·G`, which every signer can apply to their own share
 * at signing time. The [TweakCache] is what carries that, and a signing session
 * needs the cache these functions return -- not just the public key.
 *
 * Everything is a pure function of the threshold key and the path, so every
 * member's device computes the same result with no agreement round, and nothing
 * has to be stored. Rederive rather than persist.
 *
 * ### Why this is not BIP32, and why it needs no chain code
 *
 * The paths read like BIP32 and are walked the same way, index by index, but a
 * BIP32 node is a key *and* a chain code and ChillDKG produces no chain code.
 * BIP32 wants one only because it computes the tweak scalar for you:
 *
 *     t = HMAC-SHA512(chaincode, serP(K_par) || ser32(i))[0:32]
 *
 * A FROST tweak takes that scalar as an input, so choosing it directly removes
 * the chain code from the problem entirely. It also removes the trap BIP32 would
 * bring here: with x-only keys there is no single obvious `serP(K_par)`, and two
 * devices picking different parity conventions would silently derive different
 * keys rather than fail.
 *
 * ### The security property this inherits
 *
 * Additive tweaking is exactly what non-hardened BIP32 does, and it carries the
 * same weakness: `k' = k + t` with a publicly computable `t` inverts, so anyone
 * who learns one derived private key recovers the threshold key and can sign as
 * the group without any quorum at all. Hardened derivation is not available --
 * it needs the parent private key, which by construction nobody has.
 *
 * So the rule is: never reconstruct a derived key in the clear. Any path that
 * could -- an export, a "reveal private key" screen, a test helper -- leaks the
 * group key, not just the key it appears to expose.
 */
object SharedKeyDerivation {
    /**
     * The path the group's Marmot admin room is derived at. Arbitrary, and has to
     * stay put: changing it orphans every room already created here, because the
     * derived key *is* the room's id and how members find it.
     */
    val MARMOT_ADMIN_GROUP_PATH: List<Long> = listOf(9420L, 0L, 0L)

    /**
     * Domain separation for the tweak scalars. Versioned so a future scheme can
     * coexist with keys already derived under this one, and distinct from any other
     * use of the same threshold key.
     */
    private const val TWEAK_TAG = "mantra/shared-key/tweak/v1"

    /**
     * A key derived from the group's threshold key, and the cache needed to sign
     * with it.
     *
     * [cache] is not an optimisation: a FROST signing session has to be created
     * with the cache carrying the same tweaks, or the partial signatures aggregate
     * to something that verifies against the wrong key. It holds no secrets and can
     * be stored, though rederiving is simpler.
     */
    data class Derived(
        val cache: TweakCache,
        val publicKey: XonlyPublicKey
    ) {
        /** The x-only key as 32-byte hex — the form nostr and Marmot both use. */
        val hex: HexKey get() = publicKey.value.toHex()
    }

    /**
     * The marker a derived room's description carries its path under.
     *
     * MIP-01's group data is a fixed TLS schema -- name, description, admins,
     * relays, image fields, a disappearing-message timer -- with no room for a
     * custom key. Inventing one would emit bytes other Marmot clients cannot
     * decode, so the path rides in the description, which is the only free text
     * MIP-01 offers.
     *
     * Worth writing down at all because the path is what rebuilds the [TweakCache]
     * a signing session needs. Today it could be recomputed from
     * [MARMOT_ADMIN_GROUP_PATH], but that only holds while the constant never
     * changes; a room records the path it was actually made under, so a later
     * scheme can coexist with rooms already created.
     */
    private const val PATH_MARKER = "Shared key path: "

    /** `m/9420/0/0`. Hardened markers are absent on purpose -- see [parsePath]. */
    fun formatPath(path: List<Long> = MARMOT_ADMIN_GROUP_PATH): String =
        path.joinToString(separator = "/", prefix = "m/")

    /**
     * The path a derived room's description records, or null if it carries none.
     *
     * Rejects hardened indices rather than tolerating them: hardened derivation
     * needs the parent private key, which in a threshold group nobody has, so a
     * path containing one was never walked and nothing should act as if it was.
     */
    fun parsePath(description: String?): List<Long>? {
        val line = description
            ?.lineSequence()
            ?.firstOrNull { it.trimStart().startsWith(PATH_MARKER) }
            ?: return null

        val path = line.trimStart().removePrefix(PATH_MARKER).trim()
        if (!path.startsWith("m/")) return null

        return path.removePrefix("m/")
            .split("/")
            .map { segment -> segment.toLongOrNull() ?: return null }
            .takeIf { it.isNotEmpty() }
    }

    /**
     * A room description that reads as prose and still records the path.
     *
     * One line of it, on its own line, so [parsePath] can find it without the rest
     * of the description constraining what anybody writes there.
     */
    fun describe(purpose: String, path: List<Long> = MARMOT_ADMIN_GROUP_PATH): String =
        "$purpose\n\n$PATH_MARKER${formatPath(path)}"

    /**
     * Walks [path] from the group's threshold key, applying one tweak per index.
     *
     * Each scalar commits to the key it is being applied to as well as the index,
     * so the steps cannot be reordered or replayed at a different depth to reach the
     * same key. x-only throughout, because the outputs are nostr keys.
     *
     * @throws IllegalArgumentException if the threshold key is not a valid point, or
     * a tweak lands on one of the vanishingly rare invalid scalars — both of which
     * mean the caller is holding something that is not a usable threshold key.
     */
    fun derive(
        thresholdPublicKey: HexKey,
        path: List<Long> = MARMOT_ADMIN_GROUP_PATH
    ): Derived {
        val root = PublicKey(ByteVector(thresholdPublicKey.hexToByteArray()))

        var cache = TweakCache.create(root)
        var current = XonlyPublicKey(root)

        path.forEach { index ->
            val scalar = tweakScalar(current, index)

            when (val tweaked = cache.tweak(scalar, isXonly = true)) {
                is Either.Left -> throw IllegalArgumentException(
                    "Could not derive $path from the group's key at index $index",
                    tweaked.value
                )

                is Either.Right -> {
                    cache = tweaked.value.first
                    current = tweaked.value.second
                }
            }
        }

        return Derived(cache = cache, publicKey = current)
    }

    /**
     * The Marmot `nostrGroupId` for a room derived at [path].
     *
     * Replaces the `RandomInstance.bytes(32)` a Marmot room is normally minted
     * with. Marmot asks that the id be unpredictable to outsiders, which this is --
     * it is a public key derived from one the group has never published -- and it
     * buys two things random cannot: every member can compute it without being told,
     * and it is a key the group can sign as, so the room's id and the identity
     * behind it are the same thing.
     */
    fun marmotGroupId(
        thresholdPublicKey: HexKey,
        path: List<Long> = MARMOT_ADMIN_GROUP_PATH
    ): HexKey = derive(thresholdPublicKey, path).hex

    /**
     * The tweak for one step: binds the tag, the key being tweaked and the index, so
     * the same index at a different point in the walk gives a different scalar.
     */
    private fun tweakScalar(parent: XonlyPublicKey, index: Long): ByteVector32 =
        ByteVector32(
            Crypto.sha256(
                TWEAK_TAG.encodeToByteArray() +
                    parent.value.toByteArray() +
                    byteArrayOf(
                        (index ushr 24).toByte(),
                        (index ushr 16).toByte(),
                        (index ushr 8).toByte(),
                        index.toByte()
                    )
            )
        )
}
