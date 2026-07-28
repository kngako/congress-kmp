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
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.repository.ChatRepository
import press.mantra.compose.repository.MantraRepository
import press.mantra.compose.ui.view.state.AddTranslationArtifactVersionUIState

class AddTranslationArtifactVersionViewModel(
    val artifactId: String,
    val chatRoomId: String,
    val activeUserPublicKey: HexKey,
    val relayHint: String?,
    initialAddTranslationArtifactVersionUIState: AddTranslationArtifactVersionUIState,
    val mantraRepository: MantraRepository,
    val chatRepository: ChatRepository,
): ViewModel() {

    var addTranslationArtifactVersionUIState: AddTranslationArtifactVersionUIState by mutableStateOf(initialAddTranslationArtifactVersionUIState)
        private set

    private val logger = Logger.withTag(TAG)

    val isActionPending: MutableState<Boolean> = mutableStateOf(false)

    fun initiateAddTranslation() {
        viewModelScope.launch(Dispatchers.IO) {
            val artifact = mantraRepository.getArtifact(artifactId)
            val localChatRoom = chatRepository.getChatRoomByIdentifier(chatRoomId)
            addTranslationArtifactVersionUIState = if (artifact == null || localChatRoom == null) {
                AddTranslationArtifactVersionUIState.Error("Couldn't find the artifact")
            } else {
                AddTranslationArtifactVersionUIState.Loaded(
                    artifact = artifact,
                    localChatRoom = localChatRoom,
                    dialects = mantraRepository.getDialects(chatRoomId),
                )
            }
        }
    }

    fun addTranslation(
        localChatRoom: LocalChatRoom,
        existingDialectId: HexKey?,
        dialectNameField: TextFieldState,
        dialectCountryField: TextFieldState,
        dialectLanguageField: TextFieldState,
        onSuccess: () -> Unit,
        onFailure: () -> Unit
    ) {
        val dialectName = dialectNameField.text.toString()
        val dialectCountry = dialectCountryField.text.toString()
        val dialectLanguage = dialectLanguageField.text.toString()

        // When no existing dialect is selected, the new-dialect fields are required.
        val creatingNewDialect = existingDialectId.isNullOrBlank()
        val newDialectIncomplete = dialectName.isBlank() || dialectCountry.isBlank() || dialectLanguage.isBlank()
        if (creatingNewDialect && newDialectIncomplete) {
            viewModelScope.launch(Dispatchers.Main) {
                onFailure.invoke()
            }

            return
        }

        // Guard against double submits from repeated FAB taps.
        if (isActionPending.value) return
        isActionPending.value = true

        viewModelScope.launch(Dispatchers.IO) {
            val translation = runCatching {
                // Reuse the selected dialect, or create a new one for the translation.
                val dialectId = if (creatingNewDialect) {
                    mantraRepository.addDialect(
                        localChatRoom = localChatRoom,
                        name = dialectName,
                        country = dialectCountry,
                        language = dialectLanguage,
                        userPublicKey = activeUserPublicKey,
                    )?.id ?: return@runCatching null
                } else {
                    existingDialectId
                }

                mantraRepository.addTranslationArtifactVersion(
                    artifactId = artifactId,
                    dialectId = dialectId,
                    chatRoomId = chatRoomId,
                    userPublicKey = activeUserPublicKey,
                )
            }.onFailure { error ->
                logger.e("Failed to add translation", error)
            }.getOrNull()

            if (translation != null) {
                dialectNameField.clearText()
                dialectCountryField.clearText()
                dialectLanguageField.clearText()
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
        private const val TAG = "AddTranslationViewModel"

        fun factory(
            activeUserPublicKey: HexKey,
            artifactId: String,
            chatRoomId: String,
            relayHint: String?,
            initialAddTranslationArtifactVersionUIState: AddTranslationArtifactVersionUIState = AddTranslationArtifactVersionUIState.Loading,
            mantraRepository: MantraRepository,
            chatRepository: ChatRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AddTranslationArtifactVersionViewModel(
                    activeUserPublicKey = activeUserPublicKey,
                    artifactId = artifactId,
                    chatRoomId = chatRoomId,
                    relayHint = relayHint,
                    initialAddTranslationArtifactVersionUIState = initialAddTranslationArtifactVersionUIState,
                    mantraRepository = mantraRepository,
                    chatRepository = chatRepository
                )
            }
        }
    }
}
