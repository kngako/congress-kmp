package ac.aux.compose.managers

import ac.aux.compose.exceptions.InvalidNostrPrivateKeyException
import androidx.datastore.core.DataStore
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip19Bech32.bech32.Bech32
import com.vitorpamplona.quartz.nip19Bech32.toNsec
import fr.acinq.bitcoin.PrivateKey
import fr.acinq.lightning.Lightning
import fr.acinq.secp256k1.Hex
import io.ktor.utils.io.core.toByteArray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

/***
 * TODO: Make this a Singleton
 */
class CredentialsManager(
    private val persistence: DataStore<Set<Credential>>,
) {

    private val scope = CoroutineScope(Dispatchers.IO)

    val credentials = persistence.data
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = runBlocking { persistence.data.first() },
        )

    private suspend fun addCredential(credential: Credential) = persistence.updateData { it + credential }

    suspend fun clearCredentials() = persistence.updateData { emptySet() }

    fun isExternalSignerCredential(npub: String) =
        checkCredentialType(npub = npub, credentialType = CredentialType.ExternalSigner)

    fun isNpubCredential(npub: String) = checkCredentialType(npub = npub, credentialType = CredentialType.PublicKey)

    suspend fun getOrCreateInternalSignerCredentials() =
        credentials.value.find { it.type == CredentialType.InternalSigner }
            ?: Lightning.randomKey().let { privateKey ->
                Credential(
                    nsec = privateKey.value.toByteArray().toNsec(),
                    npub = privateKey.publicKey().value.toByteArray().toNpub(),
                    type = CredentialType.InternalSigner
                )
            }

    private fun checkCredentialType(npub: String, credentialType: CredentialType) =
        credentials.value.find { it.npub == npub }?.type == credentialType

    suspend fun saveNsec(nostrKey: String): String {
        val (nsec, pubkey) = nostrKey.extractKeyPairFromPrivateKeyOrThrow()
        addCredential(Credential(nsec = nsec, npub = pubkey, type = CredentialType.PrivateKey))
        return pubkey.bech32ToHexOrThrow()
    }

    suspend fun saveExternalSignerNpub(npub: String): String {
        val (hexKey, bech32Key) = if (npub.startsWith("npub")) {
            npub.bech32ToHexOrThrow() to npub
        } else {
            npub to npub.hexToNpubHrp()
        }

        addCredential(Credential(nsec = null, npub = bech32Key, type = CredentialType.ExternalSigner))
        return hexKey
    }

    suspend fun saveNpub(npub: String): String {
        addCredential(Credential(nsec = null, npub = npub, type = CredentialType.PublicKey))
        return npub.bech32ToHexOrThrow()
    }

    suspend fun removeCredentialByNsec(nsec: String) =
        persistence.updateData {
            it.filterNot { cred -> cred.nsec == nsec }.toSet()
        }

    suspend fun removeCredentialByNpub(npub: String) =
        persistence.updateData {
            it.filterNot { cred -> cred.npub == npub }.toSet()
        }

    fun findOrThrow(npub: String): Credential =
        credentials.value.find { it.npub == npub }
            ?: throw IllegalArgumentException("Credential not found for $npub.")

    @Serializable
    data class Credential(
        val nsec: String?,
        val npub: String,
        val type: CredentialType = CredentialType.PrivateKey,
    )

    enum class CredentialType {
        InternalSigner,
        ExternalSigner,
        PrivateKey,
        PublicKey,
    }

}

fun String.assureValidNsec() = if (startsWith("nsec")) this else this.hexToNsecHrp()
fun String.assureValidNpub() = if (startsWith("npub")) this else this.hexToNpubHrp()
fun String.assureValidPubKeyHex() = if (startsWith("npub")) this.bech32ToHexOrThrow() else this


fun String.hexToNoteHrp() =
    Bech32.encodeBytes(
        hrp = "note",
        data = Hex.decode(this),
        encoding = Bech32.Encoding.Bech32,
    )

fun String.hexToNpubHrp() =
    Bech32.encodeBytes(
        hrp = "npub",
        data = Hex.decode(this),
        encoding = Bech32.Encoding.Bech32,
    )

fun String.hexToNsecHrp() =
    Bech32.encodeBytes(
        hrp = "nsec",
        data = Hex.decode(this),
        encoding = Bech32.Encoding.Bech32,
    )

fun String.urlToLnUrlHrp() =
    Bech32.encodeBytes(
        hrp = "lnurl",
        data = this.toByteArray(),
        encoding = Bech32.Encoding.Bech32,
    )


fun String.bech32ToHexOrThrow() = Bech32.decodeBytes(bech32 = this).second.toHex()

fun String.bech32ToHexOrNull() = runCatching { this.bech32ToHexOrThrow() }.getOrNull()

fun ByteArray.toNpub() = Bech32.encodeBytes(hrp = "npub", this, Bech32.Encoding.Bech32)

@OptIn(ExperimentalStdlibApi::class)
fun ByteArray.toHex() = Hex.encode(this)

@Throws(IllegalArgumentException::class)
fun String.bechToBytesOrThrow(hrp: String? = null): ByteArray {
    val decodedForm = Bech32.decodeBytes(this)
    hrp?.also { require(it == decodedForm.first) }
    return decodedForm.second
}

fun String.extractKeyPairFromPrivateKeyOrThrow(): Pair<String, String> {
    return try {
        val nsec = this.assureValidNsec()
        val decoded = Bech32.decodeBytes(nsec)
        val pubkey = PrivateKey(decoded.second).publicKey().value.toByteArray()
        nsec to pubkey.toNpub()
    } catch (error: IllegalArgumentException) {
        Logger.withTag("String.extractKeyPairFromPrivateKeyOrThrow").w(error) { error.message ?: "" }
        throw InvalidNostrPrivateKeyException()
    }
}