package ac.cord.auxiliary.compose.ui.view.model

import ac.cord.auxiliary.compose.database.model.intermdiate.LocalProfile
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalProfileWithFollowing
import ac.cord.auxiliary.compose.managers.SeedManager
import ac.cord.auxiliary.compose.repository.NostrRepository
import ac.cord.auxiliary.compose.ui.view.state.HomeScreenUIState
import ac.cord.auxiliary.compose.ui.view.state.WriteNewNoteUIState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ac.cord.auxiliary.compose.ui.view.state.form.WriteNewNoteFormState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

class WriteNewNoteViewModel(
    val initialWriteNewNoteUIState: WriteNewNoteUIState,
    val writeNewNoteFormState: WriteNewNoteFormState = WriteNewNoteFormState(),
    val nostrRepository: NostrRepository
): ViewModel() {
    companion object {
        private const val TAG = "WriteNewNoteViewModel"

        fun factory(
            initialWriteNewNoteUIState: WriteNewNoteUIState,
            nostrRepository: NostrRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                WriteNewNoteViewModel(
                    initialWriteNewNoteUIState,
                    nostrRepository = nostrRepository
                )
            }
        }
    }

    private val logger = Logger.withTag(TAG)

    val isActionPending: MutableState<Boolean> = mutableStateOf(false)

    var writeNewNoteUIState: WriteNewNoteUIState by mutableStateOf(initialWriteNewNoteUIState)
        private set

    fun initiate() {
        observeActiveUserProfileWithFollowing()
    }

    fun observeActiveUserProfileWithFollowing() {
        logger.d("observeActiveUserProfileWithFollowing")
        viewModelScope.launch(Dispatchers.IO) {
            val activePublicKey = SeedManager.activePublicKey().toHexKey()

            // TODO: Get note being replied too...
            // TODO: Get note being quoted...
            // TODO: Get users being mentioned...
            nostrRepository.observeProfileWithFollowing(
                activePublicKey
            ).collect { profileWithFollowing ->
                writeNewNoteUIState = if (profileWithFollowing != null) {
                    WriteNewNoteUIState.InputPrompt(
                        localProfile = profileWithFollowing
                    )
                } else {
                    WriteNewNoteUIState.Error
                }
            }
        }
    }

    fun confirmNote(localProfile: LocalProfileWithFollowing) {
        validateInput().let { isInputValid ->
            if (isInputValid) {
                writeNewNoteUIState =
                    WriteNewNoteUIState.ConfirmNote(
                        localProfile = localProfile,
                        text = writeNewNoteFormState.textField.text.value,
                    )
            }
        }

    }

    fun validateInput(): Boolean {
        if (writeNewNoteFormState.textField.text.value.isBlank()) {
            writeNewNoteFormState.textField.errorMessage.value = "Please input a note"
            return false
        } else {
            writeNewNoteFormState.textField.errorMessage.value = null
        }
        return true
    }

    fun createNewNote(
        onNostrEventPublished: () -> Unit
    ) {
        logger.d { "createAccount" }
        isActionPending.value = true

        viewModelScope.launch(Dispatchers.IO) {
            nostrRepository.createNewTextNote(
                publicKey = SeedManager.activePublicKey().toHexKey(),
                text = writeNewNoteFormState.textField.text.value,
                onSuccess = onNostrEventPublished,
                onError = {
                    writeNewNoteUIState = WriteNewNoteUIState.Error
                }
            )
        }
    }
}