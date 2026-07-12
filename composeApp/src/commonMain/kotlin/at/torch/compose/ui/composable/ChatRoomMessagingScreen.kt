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
import androidx.compose.material.icons.filled.MoreVert
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
import at.torch.compose.ui.composable.navigation.routes.ChatRoomDetailRoute
import at.torch.compose.ui.composable.navigation.routes.Route
import at.torch.compose.ui.composable.navigation.routes.SearchMemberToAddToChatRoomRoute
import at.torch.compose.ui.composable.widgets.LoadingDataIndicator
import at.torch.compose.ui.theme.TorchTheme
import at.torch.compose.ui.view.model.ChatRoomMessagingViewModel
import at.torch.compose.ui.view.state.ChatRoomMessagingUIState
import com.vitorpamplona.quartz.nip01Core.core.HexKey

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChatRoomMessagingScreen(
    activeUserPublicKey: HexKey,
    chatRoomId: String,
    relayHint: String?,
    initialChatRoomMessagingUIState: ChatRoomMessagingUIState = ChatRoomMessagingUIState.Loading,
    nostrRepository: NostrRepository,
    chatRepository: ChatRepository,
    onNavigateToRouteAndPopUpInclusive: (Route) -> Unit,
    onNavigateToRoute: (Route) -> Unit,
) {
    val chatRoomMessagingViewModel: ChatRoomMessagingViewModel = viewModel(
        factory = ChatRoomMessagingViewModel.factory(
            chatRoomId = chatRoomId,
            relayHint = relayHint,
            initialChatRoomMessagingUIState = initialChatRoomMessagingUIState,
            nostrRepository = nostrRepository,
            chatRepository = chatRepository,
            activeUserPublicKey = activeUserPublicKey
        )
    )

    when (val chatRoomDetailUIState = chatRoomMessagingViewModel.chatRoomMessagingUIState) {
        is ChatRoomMessagingUIState.Error -> {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(
                    modifier = Modifier.height(50.dp)
                )
                Text(
                    text = chatRoomDetailUIState.message,
                )
            }
        }
        is ChatRoomMessagingUIState.Loaded -> {
            val textFieldState = rememberTextFieldState()

            val chatMessageListViewModel: at.torch.compose.ui.view.model.ChatMessageListViewModel = viewModel(
                factory = at.torch.compose.ui.view.model.ChatMessageListViewModel.factory(
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
                                    // Navigate to the invite user screen...
                                    onNavigateToRoute.invoke(
                                        ChatRoomDetailRoute(
                                            chatRoomId = chatRoomId,
                                            activeUserPublicKey = activeUserPublicKey,
                                            relayHint = relayHint
                                        )
                                    )
                                }
                            ) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "View details..."
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
                            chatMessageListViewModel.RenderMessages()
                        }
                    }

                    key(true) {
                        chatMessageListViewModel.initiate()
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
                                                chatMessageListViewModel.sendMessage(
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
        ChatRoomMessagingUIState.Loading -> {
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
        ChatRoomMessagingUIState.InitiatingNewChat -> {
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
                    chatRoomMessagingViewModel.initiateNewChat(
                        onNavigateToRouteAndPopUpInclusive
                    )
                }
            }
        }
    }

    LaunchedEffect(true) {
        if (initialChatRoomMessagingUIState == ChatRoomMessagingUIState.Loading) {
            chatRoomMessagingViewModel.initiateChatRoomDetail()
        }
    }
}

@Preview
@Composable
private fun ChatRoomMessagingScreenPreview() {
    TorchTheme {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            ChatRoomMessagingScreen(
                activeUserPublicKey = "",
                chatRoomId = "publicKey",
                relayHint = null,
                initialChatRoomMessagingUIState = ChatRoomMessagingUIState.Loaded(
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
                onNavigateToRouteAndPopUpInclusive = {},
                onNavigateToRoute = {}
            )
        }
    }
}