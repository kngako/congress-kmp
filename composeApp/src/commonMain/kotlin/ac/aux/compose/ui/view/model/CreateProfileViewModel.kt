package ac.aux.compose.ui.view.model

import ac.aux.compose.database.model.NostrEvent
import ac.aux.compose.database.model.Profile
import ac.aux.compose.database.model.UnsignedNostrEvent
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
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Instant

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

            val profileEventTemplate = MetadataEvent.createNew(
                name = createProfileFormState.nameField.text.value,
                about = createProfileFormState.biographyField.text.value
            )

            val unsignedNostrEvent = nostrRepository.saveUnsignedNostrEvent(
                UnsignedNostrEvent(
                    kind = profileEventTemplate.kind,
                    createdAt = Instant.fromEpochMilliseconds(profileEventTemplate.createdAt),
                    tags = profileEventTemplate.tags,
                    content = profileEventTemplate.content
                )
            )

            isActionPending.value = false

            if (unsignedNostrEvent != null) {
                createProfileUIState.value = CreateProfileUIState.UnsignedProfile(
                    unsignedNostrEvent = unsignedNostrEvent,
                    name = createProfileFormState.nameField.text.value,
                    bio = createProfileFormState.biographyField.text.value
                )

                delay(7_500) // Seeming busy is very important...

                createProfileUIState.value = CreateProfileUIState.UnbroadcastedProfile(
                    nostrEvent = NostrEvent(
                        id = "",
                        pubKey = "",
                        kind = 0,
                        content = "Something here",
                        tags = emptyArray(),
                        sig = ""
                    ),
                    name = createProfileFormState.nameField.text.value,
                    bio = createProfileFormState.biographyField.text.value
                )

                delay(4_500) // Seeming busy is very important...

                createProfileUIState.value = CreateProfileUIState.ProfileReady(
                    profile = Profile(
                        publicKey = "",
                        nostrEventId = "",
                        displayName = createProfileFormState.nameField.text.value,
                        about = createProfileFormState.biographyField.text.value,
                    )
                )
            } else {
                createProfileUIState.value = CreateProfileUIState.Error
            }

        }
    }
}