package at.torch.compose.ui.view.model

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
import fr.acinq.bitcoin.Crypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

class ChatRoomDetailViewModel(
    val directMessageId: String,
    val activeUserPublicKey: HexKey,
    val relayHint: String?,
    initialChatRoomDetailUIState: at.torch.compose.ui.view.state.ChatRoomDetailUIState,
    val nostrRepository: at.torch.compose.repository.NostrRepository,
    val chatRepository: at.torch.compose.repository.ChatRepository
): ViewModel() {

    var chatRoomDetailUIState: at.torch.compose.ui.view.state.ChatRoomDetailUIState by mutableStateOf(initialChatRoomDetailUIState)
        private set



    private val logger = Logger.withTag(TAG)

    val isActionPending: MutableState<Boolean> = mutableStateOf(false)

    fun initiateChatRoomDetail() {
        if (Crypto.isPubKeyCompressed(directMessageId.hexToByteArray())) {
            logger.d("compressed (most likely chat room): $directMessageId")
            // Get ChatRoomById
            viewModelScope.launch(Dispatchers.IO) {
                val localChatRoom = chatRepository.getChatRoomByIdentifier(directMessageId)

                chatRoomDetailUIState = if (localChatRoom == null) {
                    _root_ide_package_.at.torch.compose.ui.view.state.ChatRoomDetailUIState.Error
                } else {
                    _root_ide_package_.at.torch.compose.ui.view.state.ChatRoomDetailUIState.Loaded(
                        localChatRoom = localChatRoom
                    )
                }

            }
        } else {
            logger.d("Uncompressed publicKey (most likely npub): $directMessageId")
            viewModelScope.launch(Dispatchers.IO) {
                // We need to get or create a chat with the user on this publicKey...
                val localChatRoom = chatRepository.getOrCreateChatRoom(
                    activeUserPublicKey = activeUserPublicKey,
                    publicKey = directMessageId,
                    relayHint = relayHint
                )

                chatRoomDetailUIState = if (localChatRoom == null) {
                    _root_ide_package_.at.torch.compose.ui.view.state.ChatRoomDetailUIState.Error
                } else {
                    _root_ide_package_.at.torch.compose.ui.view.state.ChatRoomDetailUIState.Loaded(
                        localChatRoom = localChatRoom
                    )
                }
            }
        }
    }


    companion object {
        private const val TAG = "ChatRoomDetailViewModel"

        fun factory(
            activeUserPublicKey: HexKey,
            publicKey: String,
            relayHint: String?,
            initialChatRoomDetailUIState: at.torch.compose.ui.view.state.ChatRoomDetailUIState = _root_ide_package_.at.torch.compose.ui.view.state.ChatRoomDetailUIState.Loading,
            nostrRepository: at.torch.compose.repository.NostrRepository,
            chatRepository: at.torch.compose.repository.ChatRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ChatRoomDetailViewModel(
                    activeUserPublicKey = activeUserPublicKey,
                    directMessageId = publicKey,
                    relayHint = relayHint,
                    initialChatRoomDetailUIState = initialChatRoomDetailUIState,
                    nostrRepository = nostrRepository,
                    chatRepository = chatRepository
                )
            }
        }
    }
}