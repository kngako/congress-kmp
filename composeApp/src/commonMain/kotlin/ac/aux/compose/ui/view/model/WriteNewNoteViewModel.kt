package ac.aux.compose.ui.view.model

import ac.aux.compose.database.model.NostrEvent
import ac.aux.compose.managers.SeedManager
import ac.aux.compose.repository.NostrRepository
import ac.aux.compose.ui.view.state.WriteNewNoteUIState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ac.aux.compose.ui.view.state.form.WriteNewNoteFormState
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

    var writeNewNoteUIState: MutableState<WriteNewNoteUIState> = mutableStateOf(
        initialWriteNewNoteUIState
    )

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
                    writeNewNoteUIState.value = WriteNewNoteUIState.Error
                }
            )
        }
    }
}