package press.mantra.compose.ui.view.model

import androidx.compose.foundation.text.input.TextFieldState
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
import press.mantra.compose.ui.view.state.TranslateChunkUIState

class TranslateChunkViewModel(
    val translationChapterId: String,
    val chunkId: String,
    val chatRoomId: String,
    val activeUserPublicKey: HexKey,
    val relayHint: String?,
    initialTranslateChunkUIState: TranslateChunkUIState,
    val mantraRepository: MantraRepository,
): ViewModel() {

    var translateChunkUIState: TranslateChunkUIState by mutableStateOf(initialTranslateChunkUIState)
        private set

    private val logger = Logger.withTag(TAG)

    val isActionPending: MutableState<Boolean> = mutableStateOf(false)

    fun initiateTranslateChunk() {
        viewModelScope.launch(Dispatchers.IO) {
            val originalChunk = mantraRepository.getChunk(chunkId)
            translateChunkUIState = if (originalChunk == null) {
                TranslateChunkUIState.Error("Couldn't find the chunk")
            } else {
                val existing = mantraRepository.getTranslationChunks(translationChapterId)
                    .firstOrNull { it.chunkId == chunkId }
                TranslateChunkUIState.Loaded(
                    originalChunk = originalChunk,
                    existingTranslationText = existing?.text.orEmpty(),
                )
            }
        }
    }

    fun saveTranslation(
        translationField: TextFieldState,
        onSuccess: () -> Unit,
        onFailure: () -> Unit
    ) {
        val text = translationField.text.toString()
        if (text.isBlank()) {
            viewModelScope.launch(Dispatchers.Main) {
                onFailure.invoke()
            }
            return
        }

        if (isActionPending.value) return
        isActionPending.value = true

        viewModelScope.launch(Dispatchers.IO) {
            val saved = runCatching {
                mantraRepository.saveTranslationChunk(
                    translationChapterId = translationChapterId,
                    chunkId = chunkId,
                    text = text,
                    chatRoomId = chatRoomId,
                    userPublicKey = activeUserPublicKey,
                )
            }.onFailure { error ->
                logger.e("Failed to save translation", error)
            }.getOrNull()

            if (saved != null) {
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
        private const val TAG = "TranslateChunkViewModel"

        fun factory(
            activeUserPublicKey: HexKey,
            translationChapterId: String,
            chunkId: String,
            chatRoomId: String,
            relayHint: String?,
            initialTranslateChunkUIState: TranslateChunkUIState = TranslateChunkUIState.Loading,
            mantraRepository: MantraRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                TranslateChunkViewModel(
                    activeUserPublicKey = activeUserPublicKey,
                    translationChapterId = translationChapterId,
                    chunkId = chunkId,
                    chatRoomId = chatRoomId,
                    relayHint = relayHint,
                    initialTranslateChunkUIState = initialTranslateChunkUIState,
                    mantraRepository = mantraRepository,
                )
            }
        }
    }
}
