package press.mantra.compose.ui.view.model

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.Entity
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32.entities.NSec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

class SignInToProfileViewModel(
    initialWriteNewNoteUIState: press.mantra.compose.ui.view.state.SignInToProfileUIState,
    val signInToProfileFormState: press.mantra.compose.ui.view.state.form.SignInToProfileFormState = _root_ide_package_.press.mantra.compose.ui.view.state.form.SignInToProfileFormState(),
    val nostrRepository: press.mantra.compose.repository.NostrRepository
): ViewModel() {
    companion object {
        private const val TAG = "SignInToProfileViewModel"

        fun factory(
            initialWriteNewNoteUIState: press.mantra.compose.ui.view.state.SignInToProfileUIState,
            nostrRepository: press.mantra.compose.repository.NostrRepository
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

    var signInToProfileUIState: MutableState<press.mantra.compose.ui.view.state.SignInToProfileUIState> = mutableStateOf(
        initialWriteNewNoteUIState
    )

    fun validateCredential(): Entity? {
        return signInToProfileFormState.textFieldForm.textFieldState.text.toString().let { credentialText ->
            if (credentialText.isBlank()) {
                signInToProfileFormState.textFieldForm.errorMessage.value = "Please input a credential"
                return null
            } else {
                signInToProfileFormState.textFieldForm.errorMessage.value = null
            }

            Nip19Parser.uriToRoute(signInToProfileFormState.textFieldForm.textFieldState.text.toString())?.entity
        }
    }

    fun signInToAccount(
    ) {
        logger.d { "signInToAccount" }
        validateCredential().let { credentialEntity ->
            isActionPending.value = true

            viewModelScope.launch(Dispatchers.IO) {
                when (credentialEntity) {
                    is NPub -> {
                        nostrRepository.signInToProfile(
                            publicKey = credentialEntity.hex
                        )
                    }
                    is NSec -> {
                        nostrRepository.signInToProfile(
                            publicKey = credentialEntity.toPubKeyHex()
                        )
                    }
                    else -> {
                        // TODO: Might want to give reasons for the error.
                        signInToProfileUIState.value = press.mantra.compose.ui.view.state.SignInToProfileUIState.Error
                    }
                }

            }
        }

    }
}