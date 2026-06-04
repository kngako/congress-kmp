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
import fr.acinq.bitcoin.Crypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

class ChatRoomDetailViewModel(
    val directMessageId: String,
    initialChatRoomDetailUIState: ChatRoomDetailUIState,
    val nostrRepository: NostrRepository,
    val chatRepository: ChatRepository
): ViewModel() {

    var chatRoomDetailUIState: ChatRoomDetailUIState by mutableStateOf(initialChatRoomDetailUIState)
        private set



    private val logger = Logger.withTag(TAG)

    val isActionPending: MutableState<Boolean> = mutableStateOf(false)

    fun initiateChatRoomDetail() {
        if (Crypto.isPubKeyCompressed(directMessageId.hexToByteArray())) {
            logger.d("compressed: $directMessageId")
            // Get ChatRoomById
            viewModelScope.launch(Dispatchers.IO) {
                val localChatRoom = chatRepository.getChatRoomByIdentifier(directMessageId)

                chatRoomDetailUIState = if (localChatRoom == null) {
                    ChatRoomDetailUIState.Error
                } else {
                    ChatRoomDetailUIState.Loaded(
                        localChatRoom = localChatRoom
                    )
                }

            }
        } else {
            logger.d("Not compressed key publicKey: $directMessageId")
            viewModelScope.launch(Dispatchers.IO) {
                // We need to get or create a chat with the user on this publicKey...
                val localChatRoom = chatRepository.getOrCreateChatRoom(directMessageId)

                chatRoomDetailUIState = if (localChatRoom == null) {
                    ChatRoomDetailUIState.Error
                } else {
                    ChatRoomDetailUIState.Loaded(
                        localChatRoom = localChatRoom
                    )
                }
            }
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
                    directMessageId = publicKey,
                    initialChatRoomDetailUIState = initialChatRoomDetailUIState,
                    nostrRepository = nostrRepository,
                    chatRepository = chatRepository
                )
            }
        }
    }
}