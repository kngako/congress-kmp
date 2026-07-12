package at.torch.compose.ui.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Unsubscribe
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import at.torch.compose.database.model.ChatRoom
import at.torch.compose.database.model.Participant
import at.torch.compose.database.model.Profile
import at.torch.compose.database.model.intermdiate.LocalChatRoom
import at.torch.compose.database.model.intermdiate.LocalParticipant
import at.torch.compose.repository.ChatRepository
import at.torch.compose.repository.NostrRepository
import at.torch.compose.ui.composable.navigation.routes.ImplementationPendingRoute
import at.torch.compose.ui.composable.navigation.routes.Route
import at.torch.compose.ui.composable.navigation.routes.SearchMemberToAddToChatRoomRoute
import at.torch.compose.ui.composable.widgets.LoadingDataIndicator
import at.torch.compose.ui.composable.widgets.profile.ProfileAvatar
import at.torch.compose.ui.theme.TorchTheme
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
    onNavigateToRoute: (Route) -> Unit,
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
        is ChatRoomDetailUIState.Error -> {
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
        is ChatRoomDetailUIState.Loaded -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            chatRoomDetailUIState.localChatRoom.RenderChatRoomTitleText()
                        },
                    )
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier.padding(innerPadding).fillMaxSize()
                ) {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // TODO: Add Description...
//                        item {
//                            chatRoomDetailUIState.localChatRoom.chatRoom.subject?.let {
//                                Text(
//                                    text = it
//                                )
//                            }
//                        }

                        item {
                            // Media and such should be here...
                        }
                        item {
                            Text("members")
                        }

                        item {
                            TextButton(
                                onClick = {
                                    // Navigate to the invite user screen...
                                    onNavigateToRoute.invoke(
                                        SearchMemberToAddToChatRoomRoute(
                                            chatRoomId = chatRoomId,
                                            activeUserPublicKey = activeUserPublicKey,
                                            relayHint = relayHint
                                        )
                                    )
                                }
                            ) {

                                Icon(
                                    Icons.Default.PersonAdd,
                                    contentDescription = "Invite new member"
                                )

                                Spacer(
                                    modifier = Modifier.width(10.dp)
                                )

                                Text("Invite New Member")
                            }
                        }

                        items(
                            items = chatRoomDetailUIState.localChatRoom.localParticipants,
                            key = { localParticipant -> localParticipant.participant.participantPublicKey}
                        ) { localParticipant ->

                            Card(
                                onClick = {
                                    onNavigateToRoute.invoke(
                                        ImplementationPendingRoute(
                                            "Profile detail"
                                        )
                                    )
                                }
                            ) {
                                ListItem(
                                    leadingContent = {
                                        ProfileAvatar(
                                            publicKey = localParticipant.participant.participantPublicKey,
                                            profile = localParticipant.profile
                                        )
                                    },
                                    trailingContent = {
                                        Icon(
                                            Icons.Default.ChevronRight,
                                            contentDescription = "View profile"
                                        )
                                    },
                                    headlineContent = {
                                        Text(
                                            text = localParticipant.profile?.humanReadableNameOrPubkey() ?: localParticipant.participant.participantPublicKey
                                        )
                                    },
                                    supportingContent = {
                                        localParticipant.profile?.about?.let {
                                            Text(
                                                text = it
                                            )
                                        }
                                    }

                                )
                            }

                        }


                        item {
                            HorizontalDivider()
                        }

                        item {

                            TextButton(
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.secondary
                                ),
                                enabled = true, // Disable after we leave group...
                                onClick = {
                                    onNavigateToRoute.invoke(
                                        ImplementationPendingRoute(
                                            "Leave Group"
                                        )
                                    )
                                }
                            ) {
                                Icon(
                                    Icons.Default.Unsubscribe,
                                    contentDescription = "Leave Group"
                                )

                                Spacer(
                                    modifier = Modifier.width(10.dp)
                                )

                                Text("Leave Group")
                            }
                        }

                        item {
                            TextButton(
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                enabled = true, // TODO: Only enabled after we leave group...
                                onClick = {
                                    onNavigateToRoute.invoke(
                                        ImplementationPendingRoute(
                                            "Delete Group"
                                        )
                                    )
                                }
                            ) {
                                Icon(
                                    Icons.Default.DeleteForever,
                                    contentDescription = "Delete Group"
                                )

                                Spacer(
                                    modifier = Modifier.width(10.dp)
                                )

                                Text("Delete Group")
                            }
                        }
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
    }

    LaunchedEffect(true) {
        if (initialChatRoomDetailUIState == ChatRoomDetailUIState.Loading) {
            chatRoomDetailViewModel.initiateChatRoomDetail()
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
            ChatRoomDetailScreen(
                activeUserPublicKey = "",
                chatRoomId = "publicKey",
                relayHint = null,
                initialChatRoomDetailUIState = ChatRoomDetailUIState.Loaded(
                    localChatRoom = LocalChatRoom(
                        chatRoom = ChatRoom(
                            id = "",
                            userPublicKey = "",
                            subject = "Message title",
                            initialGiftWrapPayloadId = "sdfaer",
                            mlsGroupState = null
                        ),
                        localParticipants = listOf(
                            LocalParticipant(
                                participant = Participant(
                                    id = 2,
                                    participantPublicKey = "gex",
                                    chatRoomId = "chatRoomId",
                                    relayHint = null
                                ),
                                profile = Profile(
                                    publicKey = "gex",
                                    displayName = "Gexie",
                                    nostrEventId = "nosrtEventId"
                                )
                            )
                        )
                    )
                ),
                nostrRepository = NostrRepository.NO_OP_NOSTR_REPOSITORY,
                chatRepository = ChatRepository.NO_OP_CHAT_REPOSITORY,
                onNavigateToRoute = {}
            )
        }
    }
}