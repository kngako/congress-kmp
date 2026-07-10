package at.torch.compose.ui.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import at.torch.compose.database.model.ChatRoom
import at.torch.compose.database.model.intermdiate.LocalChatRoom
import at.torch.compose.repository.ChatRepository
import at.torch.compose.repository.NostrRepository
import at.torch.compose.ui.composable.navigation.routes.Route
import at.torch.compose.ui.composable.widgets.LoadingDataIndicator
import at.torch.compose.ui.composable.widgets.profile.ProfileColor
import at.torch.compose.ui.theme.TorchTheme
import at.torch.compose.ui.view.model.AddMemberToChatRoomConfirmationViewModel
import at.torch.compose.ui.view.state.AddMemberToChatRoomConfirmationUIState
import at.torch.compose.ui.view.state.ChatRoomDetailUIState
import com.vitorpamplona.quartz.nip01Core.core.HexKey

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AddMemberToChatRoomConfirmationScreen(
    activeUserPublicKey: HexKey,
    chatRoomId: String,
    relayHint: String?,
    initialChatRoomDetailUIState: AddMemberToChatRoomConfirmationUIState = AddMemberToChatRoomConfirmationUIState.Loading,
    nostrRepository: NostrRepository,
    chatRepository: ChatRepository,
    onNavigateToChat: (Route) -> Unit,
) {
    val addMemberToChatRoomConfirmationViewModel: AddMemberToChatRoomConfirmationViewModel = viewModel(
        factory = AddMemberToChatRoomConfirmationViewModel.factory(
            chatRoomId = chatRoomId,
            relayHint = relayHint,
            initialAddMemberToChatRoomConfirmationUIState = initialChatRoomDetailUIState,
            nostrRepository = nostrRepository,
            chatRepository = chatRepository,
            activeUserPublicKey = activeUserPublicKey
        )
    )

    when (val addMemberToChatRoomConfirmationUIState = addMemberToChatRoomConfirmationViewModel.addMemberToChatRoomConfirmationUIState) {
        is AddMemberToChatRoomConfirmationUIState.Error -> {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(
                    modifier = Modifier.height(50.dp)
                )
                Text(
                    text = addMemberToChatRoomConfirmationUIState.message,
                )
            }
        }
        is AddMemberToChatRoomConfirmationUIState.Loaded -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            val title = if (addMemberToChatRoomConfirmationUIState.localChatRoom.chatRoom.subject != null) {
                                "Invite new member to ${addMemberToChatRoomConfirmationUIState.localChatRoom.chatRoom.subject}"
                            } else {
                                "Invite new member to chat"
                            }
                            Text(
                                text = title,
                            )
                        },
                        actions = {
                        }
                    )
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier.padding(innerPadding).fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Render confirmation")
                    }
                }
            }
        }
        AddMemberToChatRoomConfirmationUIState.Loading -> {
            Column(
                modifier = Modifier.fillMaxWidth().padding(
                    20.dp
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {

                Spacer(
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Direct Message Detail",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Direct message functionality will be here.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )

                LoadingDataIndicator(
                    fillScreen = false
                )

                Spacer(
                    modifier = Modifier.weight(2f)
                )
            }
        }
    }

    LaunchedEffect(true) {
        if (initialChatRoomDetailUIState == AddMemberToChatRoomConfirmationUIState.Loading) {
            addMemberToChatRoomConfirmationViewModel.initiateChatRoomDetail()
        }
    }
}

@Preview
@Composable
private fun ChatRoomDetailScreenPreview() {
    TorchTheme {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            AddMemberToChatRoomConfirmationScreen(
                activeUserPublicKey = "",
                chatRoomId = "publicKey",
                relayHint = null,
                initialChatRoomDetailUIState = AddMemberToChatRoomConfirmationUIState.Loaded(
                    localChatRoom = LocalChatRoom(
                        chatRoom = ChatRoom(
                            id = "",
                            userPublicKey = "",
                            subject = "Message title",
                            initialGiftWrapPayloadId = "sdfaer",
                            mlsGroupState = null
                        ),
                    )
                ),
                nostrRepository = NostrRepository.NO_OP_NOSTR_REPOSITORY,
                chatRepository = ChatRepository.NO_OP_CHAT_REPOSITORY,
                onNavigateToChat = {}
            )
        }
    }
}