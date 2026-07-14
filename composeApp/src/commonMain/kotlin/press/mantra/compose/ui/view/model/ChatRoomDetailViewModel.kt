package press.mantra.compose.ui.view.model

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.repository.ChatRepository
import press.mantra.compose.repository.NostrRepository
import press.mantra.compose.ui.composable.navigation.routes.HomeRoute
import press.mantra.compose.ui.composable.navigation.routes.Route
import press.mantra.compose.ui.view.state.ChatRoomDetailUIState
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
    val nostrRepository: NostrRepository,
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

            logger.d("localChatRoom: $localChatRoom")
            chatRoomDetailUIState = if (localChatRoom == null) {
                // Initiate new chat... and navigate to new chat...
                ChatRoomDetailUIState.Error("Missing chat room")
            } else {
                ChatRoomDetailUIState.Loaded(
                    localChatRoom = localChatRoom
                )
            }
        }
    }

    fun leaveGroup(
        localChatRoom: LocalChatRoom,
        onPopBackToRoute: (Route) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            chatRepository.leaveChatRoom(
                localChatRoom,
                onCompletion = {
                    viewModelScope.launch(Dispatchers.Main) {
                        onPopBackToRoute.invoke(
                            HomeRoute(
                                activeUserPublicKey = activeUserPublicKey,
                            )
                        )
                    }
                }
            )
        }
    }

    fun softDeleteGroup(
        localChatRoom: LocalChatRoom,
        onPopBackToRoute: (Route) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            chatRepository.softDeleteChatRoom(
                chatRoom = localChatRoom.chatRoom,
                onCompletion = {
                    viewModelScope.launch(Dispatchers.Main) {
                        onPopBackToRoute.invoke(
                            HomeRoute(
                                activeUserPublicKey = activeUserPublicKey,
                            )
                        )
                    }
                }
            )
        }
    }


    companion object {
        private const val TAG = "ChatRoomDetailViewModel"

        fun factory(
            activeUserPublicKey: HexKey,
            chatRoomId: String,
            relayHint: String?,
            initialChatRoomDetailUIState: ChatRoomDetailUIState = ChatRoomDetailUIState.Loading,
            nostrRepository: NostrRepository,
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