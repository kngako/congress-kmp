package ac.aux.compose.managers

import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import kotlin.random.Random

object SeedManager {

    private val tempDevelopmentKeyPair = KeyPair(
        privKey = Random(21_012_256L).nextBytes(32)
    )

    fun activeKeyPair(): KeyPair {
        // TODO: Actually set and get a pair...
        return tempDevelopmentKeyPair
    }

    fun activePublicKey(): ByteArray {
        return activeKeyPair().pubKey
    }
}