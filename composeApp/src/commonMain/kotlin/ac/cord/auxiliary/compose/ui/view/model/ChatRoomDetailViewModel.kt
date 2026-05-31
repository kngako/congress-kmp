package ac.cord.auxiliary.compose.ui.view.model

import ac.cord.auxiliary.compose.repository.ChatRepository
import ac.cord.auxiliary.compose.repository.NostrRepository
import ac.cord.auxiliary.compose.ui.view.state.ChatRoomDetailUIState
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
import kotlinx.coroutines.launch

class ChatRoomDetailViewModel(
    val publicKey: String,
    initialChatRoomDetailUIState: ChatRoomDetailUIState,
    val nostrRepository: NostrRepository,
    val chatRepository: ChatRepository
): ViewModel() {

    var chatRoomDetailUIState: ChatRoomDetailUIState by mutableStateOf(initialChatRoomDetailUIState)
        private set



    private val logger = Logger.withTag(TAG)

    val isActionPending: MutableState<Boolean> = mutableStateOf(false)

    fun queueSynchronization() {
        logger.d { "queueSynchronization" }
    }

    // TODO: Render MessageList...
    fun initiateChatRoomDetail() {
        // TODO: GetOrCreateChatRoom
        viewModelScope.launch {
            val localChatRoom = chatRepository.getOrCreateChatRoom(publicKey)

            chatRoomDetailUIState = if (localChatRoom == null) {
                ChatRoomDetailUIState.Error
            } else {
                ChatRoomDetailUIState.Loaded(
                    localChatRoom = localChatRoom
                )
            }

            queueSynchronization()
        }
    }


    companion object {
        private const val TAG = "ChatRoomDetailViewModel"

        fun factory(
            publicKey: String,
            initialChatRoomDetailUIState: ChatRoomDetailUIState = ChatRoomDetailUIState.Loading,
            nostrRepository: NostrRepository,
            chatRepository: ChatRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ChatRoomDetailViewModel(
                    publicKey = publicKey,
                    initialChatRoomDetailUIState = initialChatRoomDetailUIState,
                    nostrRepository = nostrRepository,
                    chatRepository = chatRepository
                )
            }
        }
    }
}