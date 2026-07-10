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
import at.torch.compose.ui.view.state.SearchMemberToAddToChatRoomUIState
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

class SearchMemberToAddToChatRoomViewModel(
    val chatRoomId: String,
    val activeUserPublicKey: HexKey,
    val relayHint: String?,
    initialSearchMemberToAddToChatRoomUIState: SearchMemberToAddToChatRoomUIState,
    val nostrRepository: NostrRepository,
    val chatRepository: ChatRepository
): ViewModel() {

    var searchMemberToAddToChatRoomUIState: SearchMemberToAddToChatRoomUIState by mutableStateOf(initialSearchMemberToAddToChatRoomUIState)
        private set



    private val logger = Logger.withTag(TAG)

    val isActionPending: MutableState<Boolean> = mutableStateOf(false)

    fun initiateChatRoomDetail() {
        logger.d("compressed (most likely chat room): $chatRoomId")
        // Get ChatRoomById
        viewModelScope.launch(Dispatchers.IO) {
            val localChatRoom = chatRepository.getChatRoomByIdentifier(chatRoomId)

            searchMemberToAddToChatRoomUIState = if (localChatRoom == null) {
                // Initiate new chat... and navigate to new chat...
                SearchMemberToAddToChatRoomUIState.Error("Failed to load chat")
            } else {
                SearchMemberToAddToChatRoomUIState.Loaded(
                    localChatRoom = localChatRoom,
                    contacts = nostrRepository.searchableProfiles(
                        excludingPublicKeys = localChatRoom.localParticipants.map { it.participant.participantPublicKey }.toTypedArray()
                    )
                )
            }
        }
    }

    companion object {
        private const val TAG = "SearchMemberToAddToChatRoomViewModel"

        fun factory(
            activeUserPublicKey: HexKey,
            chatRoomId: String,
            relayHint: String?,
            initialSearchMemberToAddToChatRoomUIState: SearchMemberToAddToChatRoomUIState = SearchMemberToAddToChatRoomUIState.Loading,
            nostrRepository: NostrRepository,
            chatRepository: ChatRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SearchMemberToAddToChatRoomViewModel(
                    activeUserPublicKey = activeUserPublicKey,
                    chatRoomId = chatRoomId,
                    relayHint = relayHint,
                    initialSearchMemberToAddToChatRoomUIState = initialSearchMemberToAddToChatRoomUIState,
                    nostrRepository = nostrRepository,
                    chatRepository = chatRepository
                )
            }
        }
    }
}