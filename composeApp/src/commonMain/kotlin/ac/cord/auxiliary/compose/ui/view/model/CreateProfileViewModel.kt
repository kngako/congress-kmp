package ac.cord.auxiliary.compose.ui.view.model

import ac.cord.auxiliary.compose.managers.SeedManager
import ac.cord.auxiliary.compose.nostr.Relays
import ac.cord.auxiliary.compose.repository.NostrRepository
import ac.cord.auxiliary.compose.ui.view.state.CreateProfileUIState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ac.cord.auxiliary.compose.ui.view.state.form.CreateProfileFormState
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

class CreateProfileViewModel(
    val initialCreateProfileUIState: CreateProfileUIState,
    val createProfileFormState: CreateProfileFormState = CreateProfileFormState(),
    val nostrRepository: NostrRepository
): ViewModel() {
    companion object {
        private const val TAG = "CreateAccountViewModel"

        fun factory(
            initialCreateProfileUIState: CreateProfileUIState,
            nostrRepository: NostrRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                CreateProfileViewModel(
                    initialCreateProfileUIState,
                    nostrRepository = nostrRepository
                )
            }
        }
    }

    private val logger = Logger.withTag(TAG)

    val isActionPending: MutableState<Boolean> = mutableStateOf(false)

    var createProfileUIState: MutableState<CreateProfileUIState> = mutableStateOf(
        initialCreateProfileUIState
    )

    fun validateInput(): Boolean {
        if (createProfileFormState.nameField.textFieldState.text.isBlank()) {
            createProfileFormState.nameField.errorMessage.value = "Please input a display name"
            return false
        } else {
            createProfileFormState.nameField.errorMessage.value = null
        }

        if (createProfileFormState.biographyField.textFieldState.text.isBlank()) {
            createProfileFormState.biographyField.errorMessage.value = "Please input a biography"
            return false
        } else {
            createProfileFormState.biographyField.errorMessage.value = null
        }

        return true
    }

    public fun createAccount(
//        onNavigateToUnsignedProfile: (UnsignedProfileRoute) -> Unit,
//        onNavigateToUnannouncedProfile: (UnannouncedProfileRoute) -> Unit
    ) {
        logger.d { "createAccount" }
        isActionPending.value = true

        viewModelScope.launch(Dispatchers.IO) {
            val publicKey = SeedManager.activePublicKey().toHexKey()

            nostrRepository.createNewProfile(
                publicKey,
                name = createProfileFormState.nameField.textFieldState.text.toString(),
                biography = createProfileFormState.biographyField.textFieldState.text.toString(),
                onError = {
                    createProfileUIState.value = CreateProfileUIState.Error
                },
                { profile ->
                    createProfileUIState.value = CreateProfileUIState.ProfileReady(
                        profile = profile
                    )
                },
                synchronizationRelayURLs = Relays.eventPublishRelaySet.map { normalizedRelayUrl -> normalizedRelayUrl.url }
            )
        }
    }

    fun wipeDatabase(
        onNavigateToEndThis: () -> Unit
    ) {
        viewModelScope.launch {
            nostrRepository.wipeDatabase()
            onNavigateToEndThis.invoke()
        }
    }
}