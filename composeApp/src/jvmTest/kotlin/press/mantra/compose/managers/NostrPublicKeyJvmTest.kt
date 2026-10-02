package press.mantra.compose.managers

import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import fr.acinq.bitcoin.Chain
import fr.acinq.bitcoin.MnemonicCode
import fr.acinq.bitcoin.byteVector
import fr.acinq.lightning.crypto.LocalKeyManager
import fr.acinq.phoenix.managers.NodeParamsManager
import fr.acinq.phoenix.managers.nostrPrivateKey
import fr.acinq.phoenix.managers.nostrPublicKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `LocalKeyManager.nostrPublicKey()` is what the app publishes as a profile's
 * identity, and since lightning-kmp-app `84cc44c` it comes from the library rather
 * than from a copy of its own. This pins the contract from the consuming side:
 * x-only, sixty-four lowercase hex characters, NIP-06's published answer for
 * NIP-06's own mnemonics, and the same key quartz -- the stack the app signs with
 * -- derives from that secret. A library change back to the 33-byte compressed
 * form, or off the NIP-06 path, fails here before it renames every profile.
 */
class NostrPublicKeyJvmTest {

    private fun keyManager(
        mnemonics: String,
        chain: Chain = NodeParamsManager.chain,
        swapInXpub: String = NodeParamsManager.remoteSwapInXpub,
    ) = LocalKeyManager(MnemonicCode.toSeed(mnemonics, "").byteVector(), chain, swapInXpub)

    // NIP-06's two test vectors: empty passphrase, path m/44'/1237'/0'/0/0 -- which is
    // the mainnet path, and the app ships on mainnet.
    @Test
    fun `is NIP-06's answer for its twelve-word vector`() {
        assertEquals(Chain.Mainnet, NodeParamsManager.chain)
        assertEquals(
            "17162c921dc4d2518f9a101db33695df1afb56ab82f5ff3e5da6eec3ca5cd917",
            keyManager("leader monkey parrot ring guide accident before fence cannon height naive bean")
                .nostrPublicKey()
        )
    }

    @Test
    fun `is NIP-06's answer for its twenty-four-word vector`() {
        assertEquals(
            "d41b22899549e1f3d335a31002cfd382174006e166d3e658e3a5eecdb6463573",
            keyManager(
                "what bleak badge arrange retreat wolf trade produce cricket blur garlic valid " +
                    "proud rude strong choose busy staff weather area salt hollow arm fade"
            ).nostrPublicKey()
        )
    }

    // Off mainnet the library derives from account 1', so there is no published answer;
    // the shape and the agreement with quartz still have to hold there.
    @Test
    fun `is the x-only key quartz derives from the same secret, on every chain`() {
        val mnemonics = "leader monkey parrot ring guide accident before fence cannon height naive bean"
        val testnetSwapInXpub = "tpubDDt5vQap1awkyDXx1z1cP7QFKSZHDCCpbU8nSq9jy7X2grTjUVZDePexf6gc6AHtRRzkgfPW87K6EKUVV6t3Hu2hg7YkHkmMeLSfrP85x41"
        for (km in listOf(keyManager(mnemonics), keyManager(mnemonics, Chain.Testnet3, testnetSwapInXpub))) {
            val key = km.nostrPublicKey()
            assertTrue(key.matches(Regex("[0-9a-f]{64}")), "${km.chain}: not 32 bytes of lowercase hex: $key")
            assertEquals(
                KeyPair(privKey = km.nostrPrivateKey().value.toByteArray()).pubKey.toHexKey(),
                key,
                "${km.chain}: quartz derives a different key from the same secret"
            )
        }
    }
}
