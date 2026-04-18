package ac.aux.compose.repository

import ac.aux.compose.cryptography.signOrThrow
import ac.aux.compose.database.model.NostrEvent
import ac.aux.compose.database.model.UnsignedNostrEvent
import ac.aux.compose.exceptions.SignatureException
import ac.aux.compose.exceptions.SigningKeyNotFoundException
import ac.aux.compose.exceptions.SigningRejectedException
import ac.aux.compose.managers.CredentialsManager
import ac.aux.compose.managers.hexToNpubHrp
import ac.aux.compose.network.UserAgent
import ac.aux.compose.network.asClientTag
import ac.aux.compose.network.dto.RelayDTO
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip51Lists.relayLists.RelayFeedsListEvent
import fr.acinq.secp256k1.Hex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * As seen in Primal
 *
 * https://github.com/PrimalHQ/primal-android-app/blob/86e8d29df7f780e8d19ccff2362d7dc6398da634/app/src/main/kotlin/net/primal/android/nostr/notary/NostrNotary.kt
 *
 * TODO: Make this a Singleton
 */
class NostrNotaryRepository(
    private val nostrRepository: NostrRepository,
    private val credentialsStore: CredentialsManager,
) {
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _effects = Channel<NotarySideEffect>()
    val effects = _effects.receiveAsFlow()
    private fun setEffect(effect: NotarySideEffect) = scope.launch { _effects.send(effect) }

    private val signMutex = Mutex()

    private val _responses = Channel<SignResult>()
    private fun setResponse(response: SignResult) = scope.launch { _responses.send(response) }

    suspend fun signNostrEvent(unsignedNostrEvent: UnsignedNostrEvent): SignResult {
        val result = try {
            signNostrEvent(publicKey = unsignedNostrEvent.pubKey, event = unsignedNostrEvent)
        } catch (error: SignatureException) {
            return SignResult.Rejected(error)
        }

        return if (result != null) {
            SignResult.Signed(result)
        } else {
            signMutex.withLock {
                setEffect(NotarySideEffect.RequestSignature(unsignedNostrEvent))
                _responses.receive()
            }
        }
    }

    fun verifySignature(nostrEvent: NostrEvent): Boolean {
        throw NotImplementedError()
    }

    fun onSuccess(nostrEvent: NostrEvent) {
        setResponse(SignResult.Signed(nostrEvent))
    }

    fun onFailure() {
        setResponse(SignResult.Rejected(SigningRejectedException()))
    }

    private fun findNsecOrThrow(pubkey: String): String =
        runCatching {
            val npub = Hex.decode(pubkey).toNpub()
            credentialsStore.findOrThrow(npub = npub).nsec
        }.getOrNull() ?: throw SigningKeyNotFoundException()

    private fun signNostrEvent(publicKey: String, event: UnsignedNostrEvent): NostrEvent? {
        val isExternalSignerLogin = runCatching {
            credentialsStore.isExternalSignerCredential(npub = publicKey.hexToNpubHrp())
        }.getOrDefault(false)

        if (isExternalSignerLogin) {
            throw NotImplementedError()
        }

        return event.signOrThrow(nsec = findNsecOrThrow(publicKey))
    }

    suspend fun signRelayListMetadata(userId: String, relays: List<RelayDTO>): SignResult {
        return signNostrEvent(
            unsignedNostrEvent = UnsignedNostrEvent(
                pubKey = userId,
                content = "",
                kind = RelayFeedsListEvent.KIND,
                tags = relays.map {
                    arrayOf(
                        "r",
                        it.url,
                        when {
                            it.read -> "read"
                            it.write -> "write"
                            else -> ""
                        }
                    )
                }.toTypedArray() + listOf(UserAgent.CLIENT_NAME.asClientTag()),
            ),
        )
    }

    sealed class NotarySideEffect {
        data class RequestSignature(val unsignedEvent: UnsignedNostrEvent) : NotarySideEffect()
    }

    sealed class SignResult {
        data class Signed(val event: NostrEvent) : SignResult()
        data class Rejected(val error: SignatureException) : SignResult()

        fun unwrapOrThrow(onFailure: ((SignatureException) -> Unit)? = null): NostrEvent =
            when (this) {
                is Rejected -> {
                    onFailure?.invoke(this.error)
                    throw this.error
                }

                is Signed -> {
                    this.event
                }
            }

        fun getOrNull(onFailure: ((SignatureException) -> Unit)? = null): NostrEvent? =
            when (this) {
                is Rejected -> {
                    onFailure?.invoke(this.error)
                    null
                }

                is Signed -> {
                    this.event
                }
            }

        fun getOrThrow(error: Throwable) =
            when (this) {
                is Rejected -> throw error
                is Signed -> this.event
            }
    }
}