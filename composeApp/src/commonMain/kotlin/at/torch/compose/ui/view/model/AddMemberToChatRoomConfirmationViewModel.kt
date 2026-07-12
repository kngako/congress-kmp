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
import at.torch.compose.database.model.MarmotKeyPackage
import at.torch.compose.database.model.intermdiate.LocalChatRoom
import at.torch.compose.repository.ChatRepository
import at.torch.compose.repository.NostrRepository
import at.torch.compose.ui.view.state.AddMemberToChatRoomConfirmationUIState
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class AddMemberToChatRoomConfirmationViewModel(
    val chatRoomId: String,
    val activeUserPublicKey: HexKey,
    val profilePublicKey: HexKey,
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

            addMemberToChatRoomConfirmationUIState = if (localChatRoom != null) {
                val profile = nostrRepository.getProfileWithPublicKey(profilePublicKey)

                if (profile != null) {
                    val marmotKeyPackage = chatRepository.getMarmotKeyPackageForPublicKey(profilePublicKey)

                    if (marmotKeyPackage != null) {
                        AddMemberToChatRoomConfirmationUIState.Loaded(
                            localChatRoom = localChatRoom,
                            profile = profile,
                            marmotKeyPackage = marmotKeyPackage
                        )
                    } else {
                        AddMemberToChatRoomConfirmationUIState.Error("Profile missing key package event. Try again later or tell ${profile.humanReadableNameOrPubkey()} to use Torch.")
                    }
                } else {
                    AddMemberToChatRoomConfirmationUIState.Error("Failed to find profile...")
                }
            } else {
                AddMemberToChatRoomConfirmationUIState.Error("Couldn't load chat")
            }
        }
    }

    fun inviteToChatRoom(
        localChatRoom: LocalChatRoom,
        peerPublicKey: HexKey,
        peerKeyPackage: MarmotKeyPackage,
        onInviteSent: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            // Invite member
            chatRepository.inviteMember(
                localChatRoom = localChatRoom,
                peerPublicKey = peerPublicKey,
                peerKeyPackage = peerKeyPackage
            )
            viewModelScope.launch(Dispatchers.Main) {
                onInviteSent.invoke()
            }
        }
    }

    companion object {
        private const val TAG = "AddMemberToChatRoomConfirmationViewModel"

        fun factory(
            activeUserPublicKey: HexKey,
            profilePublicKey: HexKey,
            chatRoomId: HexKey,
            relayHint: String?,
            initialAddMemberToChatRoomConfirmationUIState: AddMemberToChatRoomConfirmationUIState = AddMemberToChatRoomConfirmationUIState.Loading,
            nostrRepository: NostrRepository,
            chatRepository: ChatRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AddMemberToChatRoomConfirmationViewModel(
                    activeUserPublicKey = activeUserPublicKey,
                    chatRoomId = chatRoomId,
                    profilePublicKey = profilePublicKey,
                    relayHint = relayHint,
                    initialAddMemberToChatRoomConfirmationUIState = initialAddMemberToChatRoomConfirmationUIState,
                    nostrRepository = nostrRepository,
                    chatRepository = chatRepository,
                )
            }
        }
    }
}