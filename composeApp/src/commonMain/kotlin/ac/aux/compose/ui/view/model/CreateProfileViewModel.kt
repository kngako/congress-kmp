package ac.aux.compose.ui.view.model

import ac.aux.compose.managers.SeedManager
import ac.aux.compose.repository.NostrRepository
import ac.aux.compose.ui.view.state.CreateProfileUIState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ac.aux.compose.ui.view.state.form.CreateProfileFormState
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
        if (createProfileFormState.nameField.text.value.isBlank()) {
            createProfileFormState.nameField.errorMessage.value = "Please input a display name"
            return false
        } else {
            createProfileFormState.nameField.errorMessage.value = null
        }

        if (createProfileFormState.biographyField.text.value.isBlank()) {
            createProfileFormState.biographyField.errorMessage.value = "Please input a biography"
            return false
        } else {
            createProfileFormState.biographyField.errorMessage.value = null
        }

        return true
    }

    public fun createAccount() {
        logger.d { "createAccount" }
        isActionPending.value = true

        viewModelScope.launch(Dispatchers.IO) {

            nostrRepository.createNewProfile(
                SeedManager.activePublicKey().toHexKey(),
                name = createProfileFormState.nameField.text.value,
                biography = createProfileFormState.biographyField.text.value,
                onError = {
                    createProfileUIState.value = CreateProfileUIState.Error
                },
                onUnsignProfile = { unsignedNostrEvent ->
                    createProfileUIState.value = CreateProfileUIState.UnsignedProfile(
                        unsignedNostrEvent = unsignedNostrEvent,
                        name = createProfileFormState.nameField.text.value,
                        bio = createProfileFormState.biographyField.text.value
                    )
                },
                onUnbroadcastedProfile = { unbroadcastedNostrEvent ->
                    createProfileUIState.value = CreateProfileUIState.UnbroadcastedProfile(
                        nostrEvent = unbroadcastedNostrEvent,
                    )
                }
            ) { profile ->
                createProfileUIState.value = CreateProfileUIState.ProfileReady(
                    profile = profile
                )
            }
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