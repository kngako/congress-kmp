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
import at.torch.compose.repository.ChatRepository
import at.torch.compose.repository.NostrRepository
import at.torch.compose.ui.composable.navigation.routes.Route
import at.torch.compose.ui.composable.widgets.LoadingDataIndicator
import at.torch.compose.ui.view.model.ChatRoomDetailViewModel
import at.torch.compose.ui.view.state.ChatRoomDetailUIState
import com.vitorpamplona.quartz.nip01Core.core.HexKey

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChatRoomDetailScreen(
    activeUserPublicKey: HexKey,
    chatRoomId: String,
    relayHint: String?,
    initialChatRoomDetailUIState: ChatRoomDetailUIState = ChatRoomDetailUIState.Loading,
    nostrRepository: NostrRepository,
    chatRepository: ChatRepository,
    onNavigateToChat: (Route) -> Unit,
) {
    val chatRoomDetailViewModel: ChatRoomDetailViewModel = viewModel(
        factory = ChatRoomDetailViewModel.factory(
            chatRoomId = chatRoomId,
            relayHint = relayHint,
            initialChatRoomDetailUIState = initialChatRoomDetailUIState,
            nostrRepository = nostrRepository,
            chatRepository = chatRepository,
            activeUserPublicKey = activeUserPublicKey
        )
    )

    when (val chatRoomDetailUIState = chatRoomDetailViewModel.chatRoomDetailUIState) {
        ChatRoomDetailUIState.Error -> {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(
                    modifier = Modifier.height(50.dp)
                )
                Text(
                    text = "Something went wrong",
                )
            }
        }
        is ChatRoomDetailUIState.Loaded -> {
            val textFieldState = rememberTextFieldState()

            val chatRoomDetailMessageListViewModel: at.torch.compose.ui.view.model.ChatRoomDetailMessageListViewModel = viewModel(
                factory = at.torch.compose.ui.view.model.ChatRoomDetailMessageListViewModel.factory(
                    localChatRoom = chatRoomDetailUIState.localChatRoom,
                    nostrRepository = nostrRepository,
                    chatRepository = chatRepository
                )
            )

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            chatRoomDetailUIState.localChatRoom.RenderChatRoomTitleText()
                        },
                        actions = {
                            IconButton(
                                onClick = {
                                    // TODO: Navigate to the invite user screen...
                                }
                            ) {
                                Icon(
                                    Icons.Default.PersonAdd,
                                    contentDescription = "Invite memeber"
                                )
                            }
                        }
                    )
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier.padding(innerPadding).fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    ) {
                        key(true) {
                            chatRoomDetailMessageListViewModel.RenderMessages()
                        }
                    }

                    key(true) {
                        chatRoomDetailMessageListViewModel.initiate()
                    }

                    // TODO: Check that we have direct message relays for this user...

                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .background(BottomAppBarDefaults.containerColor)
                            .navigationBarsPadding()
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                modifier = Modifier.fillMaxWidth(),
                                state = textFieldState,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    disabledBorderColor = Color.Transparent
                                ),
                                placeholder = {
                                    Text(
                                        text = "Say what now?"
                                    )
                                },
                                trailingIcon = {
                                    if (textFieldState.text.isEmpty()) {
                                        IconButton(
                                            onClick = {

                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.Mic,
                                                contentDescription = "Voice"
                                            )
                                        }

                                    } else {
                                        IconButton(
                                            onClick = {
                                                chatRoomDetailMessageListViewModel.sendMessage(
                                                    textFieldState = textFieldState
                                                )
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.Send,
                                                contentDescription = "Send"
                                            )
                                        }
                                    }
                                }
                            )
                        }

//                        Row(
//                            modifier = Modifier.padding(4.dp),
//                            horizontalArrangement = Arrangement.spacedBy(4.dp)
//                        ) {
//                            IconButton(
//                                onClick = {
//
//                                }
//                            ) {
//                                Icon(
//                                    Icons.Default.InsertEmoticon,
//                                    contentDescription = "Add emoji"
//                                )
//                            }
//
//                            IconButton(
//                                onClick = {
//
//                                }
//                            ) {
//                                Icon(
//                                    Icons.Default.AlternateEmail,
//                                    contentDescription = "Mention profile"
//                                )
//                            }
//
//                            IconButton(
//                                onClick = {
//
//                                }
//                            ) {
//                                Icon(
//                                    Icons.Default.Collections,
//                                    contentDescription = "Attach media"
//                                )
//                            }
//
//                            IconButton(
//                                onClick = {
//
//                                }
//                            ) {
//                                Icon(
//                                    Icons.Default.LocationOn,
//                                    contentDescription = "Send location"
//                                )
//                            }
//                        }
                    }
                }
            }
        }
        ChatRoomDetailUIState.Loading -> {
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
        ChatRoomDetailUIState.InitiatingNewChat -> {
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
                    text = "Creating new chat.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )

                LoadingDataIndicator(
                    fillScreen = false
                )

                Spacer(
                    modifier = Modifier.weight(2f)
                )

                LaunchedEffect(true) {
                    chatRoomDetailViewModel.initiateNewChat(
                        onNavigateToChat
                    )
                }
            }
        }
    }

    LaunchedEffect(true) {
        if (initialChatRoomDetailUIState == ChatRoomDetailUIState.Loading) {
            chatRoomDetailViewModel.initiateChatRoomDetail()
        }
    }
}

@Preview
@Composable
private fun ChatRoomDetailScreenPreview() {
    _root_ide_package_.at.torch.compose.ui.theme.TorchTheme {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            ChatRoomDetailScreen(
                activeUserPublicKey = "",
                chatRoomId = "publicKey",
                relayHint = null,
                initialChatRoomDetailUIState = ChatRoomDetailUIState.Loaded(
                    localChatRoom = _root_ide_package_.at.torch.compose.database.model.intermdiate.LocalChatRoom(
                        chatRoom = _root_ide_package_.at.torch.compose.database.model.ChatRoom(
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