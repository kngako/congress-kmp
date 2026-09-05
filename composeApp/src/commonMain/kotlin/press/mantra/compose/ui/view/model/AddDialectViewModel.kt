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
import press.mantra.compose.repository.NostrRepository
import press.mantra.compose.ui.view.state.AddDialectUIState

class AddDialectViewModel(
    val chatRoomId: String,
    val activeUserPublicKey: HexKey,
    val relayHint: String?,
    initialAddDialectUIState: AddDialectUIState,
    val nostrRepository: NostrRepository,
    val chatRepository: ChatRepository,
    val mantraRepository: MantraRepository,
): ViewModel() {

    var addDialectUIState: AddDialectUIState by mutableStateOf(initialAddDialectUIState)
        private set

    private val logger = Logger.withTag(TAG)

    val isActionPending: MutableState<Boolean> = mutableStateOf(false)

    fun initiateAddDialect() {
        logger.d("compressed (most likely chat room): $chatRoomId")
        viewModelScope.launch(Dispatchers.IO) {
            val localChatRoom = chatRepository.getChatRoomByIdentifier(chatRoomId)

            addDialectUIState = if (localChatRoom == null) {
                AddDialectUIState.Error("Couldn't find the chat room")
            } else {
                AddDialectUIState.Loaded(
                    localChatRoom = localChatRoom,
                )
            }
        }
    }

    fun addDialect(
        localChatRoom: LocalChatRoom,
        nameField: TextFieldState,
        countryField: TextFieldState,
        languageField: TextFieldState,
        onSuccess: (dialectId: String) -> Unit,
        onFailure: () -> Unit
    ) {
        val name = nameField.text.toString()
        val country = countryField.text.toString()
        val language = languageField.text.toString()

        if (name.isBlank() || country.isBlank() || language.isBlank()) {
            onFailure.invoke()
            return
        }

        // Guard against double submits from repeated FAB taps.
        if (isActionPending.value) return
        isActionPending.value = true

        viewModelScope.launch(Dispatchers.IO) {
            val dialect = runCatching {
                mantraRepository.addDialect(
                    localChatRoom = localChatRoom,
                    name = name,
                    country = country,
                    language = language,
                    userPublicKey = activeUserPublicKey,
                )
            }.onFailure { error ->
                logger.e("Failed to add dialect", error)
            }.getOrNull()

            if (dialect != null) {
                nameField.clearText()
                countryField.clearText()
                languageField.clearText()

                viewModelScope.launch(Dispatchers.Main) {
                    onSuccess.invoke(dialect.id)
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
        private const val TAG = "AddDialectViewModel"

        fun factory(
            activeUserPublicKey: HexKey,
            chatRoomId: String,
            relayHint: String?,
            initialAddDialectUIState: AddDialectUIState = AddDialectUIState.Loading,
            nostrRepository: NostrRepository,
            chatRepository: ChatRepository,
            mantraRepository: MantraRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AddDialectViewModel(
                    activeUserPublicKey = activeUserPublicKey,
                    chatRoomId = chatRoomId,
                    relayHint = relayHint,
                    initialAddDialectUIState = initialAddDialectUIState,
                    nostrRepository = nostrRepository,
                    chatRepository = chatRepository,
                    mantraRepository = mantraRepository
                )
            }
        }
    }
}
