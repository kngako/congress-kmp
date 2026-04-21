package ac.cord.auxiliary.compose.managers

import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import kotlin.random.Random

object SeedManager {
    val logger = Logger.withTag("SeedManager")

    private val tempDevelopmentKeyPair = KeyPair(
        privKey = Random(21_012_256L).nextBytes(32)
    )

    init {
        logger.d("npub: ${tempDevelopmentKeyPair.pubKey.toNpub()}")
    }

    fun activeKeyPair(): KeyPair {
        // TODO: Actually set and get a pair...

        return tempDevelopmentKeyPair
    }

    fun activePublicKey(): ByteArray {
        return activeKeyPair().pubKey
    }
}