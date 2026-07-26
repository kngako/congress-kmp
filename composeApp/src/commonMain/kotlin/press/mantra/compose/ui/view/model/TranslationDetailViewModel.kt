package press.mantra.compose.ui.view.model

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
import press.mantra.compose.ui.view.state.TranslationChapterProgress
import press.mantra.compose.ui.view.state.TranslationDetailUIState

class TranslationDetailViewModel(
    val translationArtifactVersionId: String,
    val chatRoomId: String,
    val activeUserPublicKey: HexKey,
    val relayHint: String?,
    initialTranslationDetailUIState: TranslationDetailUIState,
    val mantraRepository: MantraRepository,
): ViewModel() {

    var translationDetailUIState: TranslationDetailUIState by mutableStateOf(initialTranslationDetailUIState)
        private set

    private val logger = Logger.withTag(TAG)

    fun initiateTranslationDetail() {
        logger.d("initiateTranslationDetail: $translationArtifactVersionId")
        viewModelScope.launch(Dispatchers.IO) {
            val translation = mantraRepository.getTranslation(translationArtifactVersionId)

            translationDetailUIState = if (translation == null) {
                TranslationDetailUIState.Error("Couldn't find the translation")
            } else {
                val chapters = mantraRepository.getTranslationChapters(translationArtifactVersionId)
                    .map { chapter ->
                        val chunks = mantraRepository.getTranslationChunks(chapter.id)
                        TranslationChapterProgress(
                            chapter = chapter,
                            totalChunks = chunks.size,
                            translatedChunks = chunks.count { it.text.isNotBlank() },
                        )
                    }
                TranslationDetailUIState.Loaded(
                    translation = translation,
                    chapters = chapters,
                )
            }
        }
    }

    companion object {
        private const val TAG = "TranslationDetailViewModel"

        fun factory(
            activeUserPublicKey: HexKey,
            translationArtifactVersionId: String,
            chatRoomId: String,
            relayHint: String?,
            initialTranslationDetailUIState: TranslationDetailUIState = TranslationDetailUIState.Loading,
            mantraRepository: MantraRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                TranslationDetailViewModel(
                    activeUserPublicKey = activeUserPublicKey,
                    translationArtifactVersionId = translationArtifactVersionId,
                    chatRoomId = chatRoomId,
                    relayHint = relayHint,
                    initialTranslationDetailUIState = initialTranslationDetailUIState,
                    mantraRepository = mantraRepository,
                )
            }
        }
    }
}
