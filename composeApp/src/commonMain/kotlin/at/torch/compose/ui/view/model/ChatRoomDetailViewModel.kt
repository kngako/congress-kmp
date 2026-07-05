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
import at.torch.compose.repository.ChatRepository
import at.torch.compose.ui.composable.navigation.routes.ChatRoomDetailRoute
import at.torch.compose.ui.composable.navigation.routes.Route
import at.torch.compose.ui.view.state.ChatRoomDetailUIState
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

class ChatRoomDetailViewModel(
    val chatRoomId: String,
    val activeUserPublicKey: HexKey,
    val relayHint: String?,
    initialChatRoomDetailUIState: ChatRoomDetailUIState,
    val nostrRepository: at.torch.compose.repository.NostrRepository,
    val chatRepository: ChatRepository
): ViewModel() {

    var chatRoomDetailUIState: ChatRoomDetailUIState by mutableStateOf(initialChatRoomDetailUIState)
        private set



    private val logger = Logger.withTag(TAG)

    val isActionPending: MutableState<Boolean> = mutableStateOf(false)

    fun initiateChatRoomDetail() {
        logger.d("compressed (most likely chat room): $chatRoomId")
        // Get ChatRoomById
        viewModelScope.launch(Dispatchers.IO) {
            val localChatRoom = chatRepository.getChatRoomByIdentifier(chatRoomId)

            chatRoomDetailUIState = if (localChatRoom == null) {
                // Initiate new chat... and navigate to new chat...
                ChatRoomDetailUIState.InitiatingNewChat
            } else {
                ChatRoomDetailUIState.Loaded(
                    localChatRoom = localChatRoom
                )
            }
        }
    }

    fun initiateNewChat(
        onNavigateToChat: (Route) -> Unit
    ) {
        logger.d("initiateNewChat: $chatRoomId")
        // Get ChatRoomById
        viewModelScope.launch(Dispatchers.IO) {

            // Check if we have a chat with activeUserPublicKey and chatRoomId (pubkey) as participants
            val participants = chatRepository.getAllParticipantsWithPubKey(chatRoomId)

            participants.forEach { participant ->
                chatRepository.getChatRoomByIdentifier(participant.chatRoomId)?.let { localChatRoom ->
                    // TODO: Check that chatRoom is not a group chat...
                    if (localChatRoom.localParticipants.size == 1) {
                        // This is a chat with self...
                        // TODO: navigateToTheChat...
                        viewModelScope.launch(Dispatchers.Main) {
                             onNavigateToChat.invoke(
                                ChatRoomDetailRoute(
                                    activeUserPublicKey = activeUserPublicKey,
                                    chatRoomId = localChatRoom.chatRoom.id,
                                    relayHint = relayHint
                                )
                            )
                        }
                        return@launch

                    } else if (localChatRoom.localParticipants.size == 2) {
                        val activeUserParticipant = localChatRoom.localParticipants.find { localParticipant -> localParticipant.participant.participantPublicKey == activeUserPublicKey }

                        if (activeUserParticipant != null) {
                            // We have found the chat we are looking for...
                            viewModelScope.launch(Dispatchers.Main) {
                                onNavigateToChat.invoke(
                                    ChatRoomDetailRoute(
                                        activeUserPublicKey = activeUserPublicKey,
                                        chatRoomId = localChatRoom.chatRoom.id,
                                        relayHint = relayHint
                                    )
                                )
                            }
                            return@launch
                        }
                    }
                }
            }

            logger.d("We should be creating a new chat...")
            chatRoomDetailUIState = ChatRoomDetailUIState.Error
        }
    }


    companion object {
        private const val TAG = "ChatRoomDetailViewModel"

        fun factory(
            activeUserPublicKey: HexKey,
            chatRoomId: String,
            relayHint: String?,
            initialChatRoomDetailUIState: ChatRoomDetailUIState = ChatRoomDetailUIState.Loading,
            nostrRepository: at.torch.compose.repository.NostrRepository,
            chatRepository: ChatRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ChatRoomDetailViewModel(
                    activeUserPublicKey = activeUserPublicKey,
                    chatRoomId = chatRoomId,
                    relayHint = relayHint,
                    initialChatRoomDetailUIState = initialChatRoomDetailUIState,
                    nostrRepository = nostrRepository,
                    chatRepository = chatRepository
                )
            }
        }
    }
}