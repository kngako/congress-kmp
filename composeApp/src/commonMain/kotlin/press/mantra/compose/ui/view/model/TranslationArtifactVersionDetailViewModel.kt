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
import press.mantra.compose.ui.view.state.TranslationArtifactVersionDetailUIState

class TranslationArtifactVersionDetailViewModel(
    val translationArtifactVersionId: String,
    val chatRoomId: String,
    val activeUserPublicKey: HexKey,
    val relayHint: String?,
    initialTranslationArtifactVersionDetailUIState: TranslationArtifactVersionDetailUIState,
    val mantraRepository: MantraRepository,
): ViewModel() {

    var translationDetailUIState: TranslationArtifactVersionDetailUIState by mutableStateOf(initialTranslationArtifactVersionDetailUIState)
        private set

    private val logger = Logger.withTag(TAG)

    fun initiateTranslationArtifactVersionDetail() {
        logger.d("initiateTranslationArtifactVersionDetail: $translationArtifactVersionId")
        viewModelScope.launch(Dispatchers.IO) {
            val translation = mantraRepository.getTranslation(translationArtifactVersionId)

            translationDetailUIState = if (translation == null) {
                TranslationArtifactVersionDetailUIState.Error("Couldn't find the translation")
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
                TranslationArtifactVersionDetailUIState.Loaded(
                    translation = translation,
                    chapters = chapters,
                )
            }
        }
    }

    companion object {
        private const val TAG = "TranslationArtifactVersionDetailViewModel"

        fun factory(
            activeUserPublicKey: HexKey,
            translationArtifactVersionId: String,
            chatRoomId: String,
            relayHint: String?,
            initialTranslationArtifactVersionDetailUIState: TranslationArtifactVersionDetailUIState = TranslationArtifactVersionDetailUIState.Loading,
            mantraRepository: MantraRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                TranslationArtifactVersionDetailViewModel(
                    activeUserPublicKey = activeUserPublicKey,
                    translationArtifactVersionId = translationArtifactVersionId,
                    chatRoomId = chatRoomId,
                    relayHint = relayHint,
                    initialTranslationArtifactVersionDetailUIState = initialTranslationArtifactVersionDetailUIState,
                    mantraRepository = mantraRepository,
                )
            }
        }
    }
}
