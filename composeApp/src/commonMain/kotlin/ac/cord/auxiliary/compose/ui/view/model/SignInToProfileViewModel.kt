package ac.cord.auxiliary.compose.ui.view.model

import ac.cord.auxiliary.compose.nostr.Relays
import ac.cord.auxiliary.compose.repository.NostrRepository
import ac.cord.auxiliary.compose.ui.view.state.SignInToProfileUIState
import ac.cord.auxiliary.compose.ui.view.state.form.SignInToProfileFormState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.Entity
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32.entities.NSec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

class SignInToProfileViewModel(
    initialWriteNewNoteUIState: SignInToProfileUIState,
    val signInToProfileFormState: SignInToProfileFormState = SignInToProfileFormState(),
    val nostrRepository: NostrRepository
): ViewModel() {
    companion object {
        private const val TAG = "SignInToProfileViewModel"

        fun factory(
            initialWriteNewNoteUIState: SignInToProfileUIState,
            nostrRepository: NostrRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SignInToProfileViewModel(
                    initialWriteNewNoteUIState,
                    nostrRepository = nostrRepository
                )
            }
        }
    }

    private val logger = Logger.withTag(TAG)

    val isActionPending: MutableState<Boolean> = mutableStateOf(false)

    var signInToProfileUIState: MutableState<SignInToProfileUIState> = mutableStateOf(
        initialWriteNewNoteUIState
    )

    fun validateCredential(): Entity? {
        return signInToProfileFormState.textField.textFieldState.text.toString().let { credentialText ->
            if (credentialText.isBlank()) {
                signInToProfileFormState.textField.errorMessage.value = "Please input a credential"
                return null
            } else {
                signInToProfileFormState.textField.errorMessage.value = null
            }

            Nip19Parser.uriToRoute(signInToProfileFormState.textField.textFieldState.text.toString())?.entity
        }
    }

    fun signInToAccount(
        onError: () -> Unit,
    ) {
        logger.d { "signInToAccount" }
        validateCredential().let { credentialEntity ->
            isActionPending.value = true

            viewModelScope.launch(Dispatchers.IO) {
                when (credentialEntity) {
                    is NPub -> {
                        nostrRepository.createNewProfile(
                            publicKey = credentialEntity.hex,
                            name = null,
                            biography = null,
                            onError = onError,
                            onProfileReady = {},
                            synchronizationRelayURLs = Relays.eventPublishRelaySet.map { normalizedRelayUrl -> normalizedRelayUrl.url }
                        )
                    }
                    is NSec -> {
                        nostrRepository.createNewProfile(
                            publicKey = credentialEntity.toPubKeyHex(),
                            name = null,
                            biography = null,
                            onError = onError,
                            onProfileReady = {},
                            synchronizationRelayURLs = Relays.eventPublishRelaySet.map { normalizedRelayUrl -> normalizedRelayUrl.url }
                        )
                    }
                    else -> {
                        // TODO: Might want to give reasons for the error.
                        signInToProfileUIState.value = SignInToProfileUIState.Error
                    }
                }

            }
        }

    }
}