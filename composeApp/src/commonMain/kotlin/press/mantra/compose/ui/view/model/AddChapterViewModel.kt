package press.mantra.compose.ui.view.model

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import press.mantra.compose.repository.MantraRepository
import press.mantra.compose.ui.view.state.AddChapterUIState

class AddChapterViewModel(
    val artifactId: String,
    val chatRoomId: String,
    val activeUserPublicKey: HexKey,
    val relayHint: String?,
    initialAddChapterUIState: AddChapterUIState,
    val mantraRepository: MantraRepository,
): ViewModel() {

    var addChapterUIState: AddChapterUIState by mutableStateOf(initialAddChapterUIState)
        private set

    private val logger = Logger.withTag(TAG)

    val isActionPending: MutableState<Boolean> = mutableStateOf(false)

    fun initiateAddChapter() {
        viewModelScope.launch(Dispatchers.IO) {
            val artifact = mantraRepository.getArtifact(artifactId)
            addChapterUIState = if (artifact == null) {
                AddChapterUIState.Error("Couldn't find the artifact")
            } else {
                AddChapterUIState.Loaded(artifact = artifact)
            }
        }
    }

    fun addChapter(
        nameField: TextFieldState,
        originalTextField: TextFieldState,
        onSuccess: () -> Unit,
        onFailure: () -> Unit
    ) {
        val name = nameField.text.toString()
        val originalText = originalTextField.text.toString()

        if (name.isBlank() || originalText.isBlank()) {
            viewModelScope.launch(Dispatchers.Main) {
                onFailure.invoke()
            }
            return
        }

        // Guard against double submits from repeated FAB taps.
        if (isActionPending.value) return
        isActionPending.value = true

        viewModelScope.launch(Dispatchers.IO) {
            val chapter = runCatching {
                mantraRepository.addChapter(
                    artifactId = artifactId,
                    name = name,
                    originalText = originalText,
                    chatRoomId = chatRoomId,
                    userPublicKey = activeUserPublicKey,
                )
            }.onFailure { error ->
                logger.e("Failed to add chapter", error)
            }.getOrNull()

            if (chapter != null) {
                nameField.clearText()
                originalTextField.clearText()
                viewModelScope.launch(Dispatchers.Main) {
                    onSuccess.invoke()
                }
            } else {
                viewModelScope.launch(Dispatchers.Main) {
                    onFailure.invoke()
                }
            }

            isActionPending.value = false
        }
    }

    companion object {
        private const val TAG = "AddChapterViewModel"

        fun factory(
            activeUserPublicKey: HexKey,
            artifactId: String,
            chatRoomId: String,
            relayHint: String?,
            initialAddChapterUIState: AddChapterUIState = AddChapterUIState.Loading,
            mantraRepository: MantraRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AddChapterViewModel(
                    activeUserPublicKey = activeUserPublicKey,
                    artifactId = artifactId,
                    chatRoomId = chatRoomId,
                    relayHint = relayHint,
                    initialAddChapterUIState = initialAddChapterUIState,
                    mantraRepository = mantraRepository,
                )
            }
        }
    }
}
