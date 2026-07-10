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
import at.torch.compose.repository.NostrRepository
import at.torch.compose.ui.composable.navigation.routes.ChatRoomDetailRoute
import at.torch.compose.ui.composable.navigation.routes.Route
import at.torch.compose.ui.view.state.AddMemberToChatRoomConfirmationUIState
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

class AddMemberToChatRoomConfirmationViewModel(
    val chatRoomId: String,
    val activeUserPublicKey: HexKey,
    val relayHint: String?,
    initialAddMemberToChatRoomConfirmationUIState: AddMemberToChatRoomConfirmationUIState,
    val nostrRepository: NostrRepository,
    val chatRepository: ChatRepository
): ViewModel() {

    var addMemberToChatRoomConfirmationUIState: AddMemberToChatRoomConfirmationUIState by mutableStateOf(initialAddMemberToChatRoomConfirmationUIState)
        private set

    private val logger = Logger.withTag(TAG)

    val isActionPending: MutableState<Boolean> = mutableStateOf(false)

    fun initiate() {
        logger.d("compressed (most likely chat room): $chatRoomId")
        // Get ChatRoomById
        viewModelScope.launch(Dispatchers.IO) {
            val localChatRoom = chatRepository.getChatRoomByIdentifier(chatRoomId)

            addMemberToChatRoomConfirmationUIState = if (localChatRoom == null) {
                // Initiate new chat... and navigate to new chat...
                AddMemberToChatRoomConfirmationUIState.Error("Couldn't load chat")
            } else {
                AddMemberToChatRoomConfirmationUIState.Loaded(
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

            val profile = chatRepository.getProfileWithPublicKey(chatRoomId)

            if (profile != null) {
                logger.d("Create chat with: $profile")
                // Check if we have a keyPackageEvent...
                val keyPackageEvent = chatRepository.getMarmotKeyPackageForPublicKey(chatRoomId)

                if (keyPackageEvent == null) {
                    addMemberToChatRoomConfirmationUIState = AddMemberToChatRoomConfirmationUIState.Error("Missing Key Package") // TODO: Introduce missing key package UI State...
                } else {
                    // Create new chat and invite local
                    try {
                        val chatRoomId = chatRepository.createMlsDirectMessage(
                            userPublicKey = activeUserPublicKey,
                            peerPublicKey = chatRoomId,
                            peerKeyPackage = keyPackageEvent,
                        )

                        onNavigateToChat.invoke(
                            ChatRoomDetailRoute(
                                activeUserPublicKey = activeUserPublicKey,
                                chatRoomId = chatRoomId,
                                relayHint = relayHint
                            )
                        )
                    } catch (e: Throwable) {
                        addMemberToChatRoomConfirmationUIState = AddMemberToChatRoomConfirmationUIState.Error("Failed to create chat")

                    }
                }

            } else {
                addMemberToChatRoomConfirmationUIState = AddMemberToChatRoomConfirmationUIState.Error("Couldn't find profile...")
            }
        }
    }


    companion object {
        private const val TAG = "AddMemberToChatRoomConfirmationViewModel"

        fun factory(
            activeUserPublicKey: HexKey,
            chatRoomId: String,
            relayHint: String?,
            initialAddMemberToChatRoomConfirmationUIState: AddMemberToChatRoomConfirmationUIState = AddMemberToChatRoomConfirmationUIState.Loading,
            nostrRepository: NostrRepository,
            chatRepository: ChatRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AddMemberToChatRoomConfirmationViewModel(
                    activeUserPublicKey = activeUserPublicKey,
                    chatRoomId = chatRoomId,
                    relayHint = relayHint,
                    initialAddMemberToChatRoomConfirmationUIState = initialAddMemberToChatRoomConfirmationUIState,
                    nostrRepository = nostrRepository,
                    chatRepository = chatRepository
                )
            }
        }
    }
}