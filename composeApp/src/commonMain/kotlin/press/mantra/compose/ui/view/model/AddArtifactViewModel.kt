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
import press.mantra.compose.repository.ChatRepository
import press.mantra.compose.repository.NostrRepository
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.repository.MantraRepository
import press.mantra.compose.repository.MarmotRepository
import press.mantra.compose.ui.view.state.AddArtifactUIState

class AddArtifactViewModel(
    val chatRoomId: String,
    val activeUserPublicKey: HexKey,
    val relayHint: String?,
    initialAddArtifactUIState: AddArtifactUIState,
    val nostrRepository: NostrRepository,
    val chatRepository: ChatRepository,
    val mantraRepository: MantraRepository,
): ViewModel() {

    var addArtifactUIState: AddArtifactUIState by mutableStateOf(initialAddArtifactUIState)
        private set

    private val logger = Logger.withTag(TAG)

    val isActionPending: MutableState<Boolean> = mutableStateOf(false)

    fun initiateChatRoomDetail() {
        logger.d("compressed (most likely chat room): $chatRoomId")
        // Get ChatRoomById
        viewModelScope.launch(Dispatchers.IO) {
            val localChatRoom = chatRepository.getChatRoomByIdentifier(chatRoomId)

            addArtifactUIState = if (localChatRoom == null) {
                // Initiate new chat... and navigate to new chat...
                AddArtifactUIState.Error("Couldn't find the chat room")
            } else {
                AddArtifactUIState.Loaded(
                    localChatRoom = localChatRoom,
                    dialects = mantraRepository.getDialects(localChatRoom.chatRoom.id),
                )
            }
        }
    }

    fun addArtifact(
        localChatRoom: LocalChatRoom,
        nameField: TextFieldState,
        urlField: TextFieldState,
        versionLabelField: TextFieldState,
        existingDialectId: HexKey?,
        dialectNameField: TextFieldState,
        dialectCountryField: TextFieldState,
        dialectLanguageField: TextFieldState,
        visibility: String = MantraRepository.DEFAULT_VISIBILITY,
        license: String = MantraRepository.DEFAULT_LICENSE,
        onSuccess: () -> Unit,
        onFailure: () -> Unit
    ) {
        val name = nameField.text.toString()
        val url = urlField.text.toString()
        val versionLabel = versionLabelField.text.toString()
        val dialectName = dialectNameField.text.toString()
        val dialectCountry = dialectCountryField.text.toString()
        val dialectLanguage = dialectLanguageField.text.toString()

        // When no existing dialect is selected, the new-dialect fields are required.
        val creatingNewDialect = existingDialectId.isNullOrBlank()
        val newDialectIncomplete = dialectName.isBlank() || dialectCountry.isBlank() || dialectLanguage.isBlank()
        if (name.isBlank() || url.isBlank() || versionLabel.isBlank() ||
            (creatingNewDialect && newDialectIncomplete)
        ) {
            onFailure.invoke()
            return
        }

        // Guard against double submits from repeated FAB taps.
        if (isActionPending.value) return
        isActionPending.value = true

        viewModelScope.launch(Dispatchers.IO) {
            val marmotInnerEvent = runCatching {
                // Reuse the selected dialect, or create a new source dialect and
                // reference it by id. addArtifact requires a valid dialectId.
                val dialectId = if (creatingNewDialect) {
                    mantraRepository.addDialect(
                        name = dialectName,
                        country = dialectCountry,
                        language = dialectLanguage,
                        chatRoomId = localChatRoom.chatRoom.id,
                        userPublicKey = activeUserPublicKey,
                    )?.id ?: return@runCatching null
                } else {
                    existingDialectId!!
                }

                mantraRepository.addArtifact(
                    name = name,
                    url = url,
                    versionLabel = versionLabel,
                    dialectId = dialectId,
                    chatRoomId = localChatRoom.chatRoom.id,
                    userPublicKey = activeUserPublicKey,
                    visibility = visibility,
                    license = license,
                )
            }.onFailure { error ->
                logger.e("Failed to add artifact", error)
            }.getOrNull()

            if (marmotInnerEvent != null) {
                nameField.clearText()
                urlField.clearText()
                versionLabelField.clearText()
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
        private const val TAG = "AddArtifactViewModel"

        fun factory(
            activeUserPublicKey: HexKey,
            chatRoomId: String,
            relayHint: String?,
            initialAddArtifactUIState: AddArtifactUIState = AddArtifactUIState.Loading,
            nostrRepository: NostrRepository,
            chatRepository: ChatRepository,
            mantraRepository: MantraRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AddArtifactViewModel(
                    activeUserPublicKey = activeUserPublicKey,
                    chatRoomId = chatRoomId,
                    relayHint = relayHint,
                    initialAddArtifactUIState = initialAddArtifactUIState,
                    nostrRepository = nostrRepository,
                    chatRepository = chatRepository,
                    mantraRepository = mantraRepository
                )
            }
        }
    }
}