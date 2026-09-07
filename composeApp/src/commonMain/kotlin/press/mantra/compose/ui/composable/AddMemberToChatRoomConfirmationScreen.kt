package press.mantra.compose.ui.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import press.mantra.compose.database.model.ChatRoom
import press.mantra.compose.database.model.MarmotKeyPackage
import press.mantra.compose.database.model.Profile
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.repository.ChatRepository
import press.mantra.compose.repository.NostrRepository
import press.mantra.compose.ui.composable.widgets.LoadingDataIndicator
import press.mantra.compose.ui.theme.TorchTheme
import press.mantra.compose.ui.view.model.AddMemberToChatRoomConfirmationViewModel
import press.mantra.compose.ui.view.state.AddMemberToChatRoomConfirmationUIState
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import press.mantra.compose.ui.theme.spacing

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AddMemberToChatRoomConfirmationScreen(
    activeUserPublicKey: HexKey,
    chatRoomId: HexKey,
    profilePublicKey: HexKey,
    relayHint: String?,
    initialChatRoomDetailUIState: AddMemberToChatRoomConfirmationUIState = AddMemberToChatRoomConfirmationUIState.Loading,
    nostrRepository: NostrRepository,
    chatRepository: ChatRepository,
    onInviteSent: () -> Unit,
) {
    val addMemberToChatRoomConfirmationViewModel: AddMemberToChatRoomConfirmationViewModel = viewModel(
        factory = AddMemberToChatRoomConfirmationViewModel.factory(
            chatRoomId = chatRoomId,
            profilePublicKey = profilePublicKey,
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
                modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.space250),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(
                    modifier = Modifier.height(MaterialTheme.spacing.space600)
                )
                Text(
                    text = addMemberToChatRoomConfirmationUIState.message,
                    textAlign = TextAlign.Center
                )
            }
        }
        is AddMemberToChatRoomConfirmationUIState.Loaded -> {
            Scaffold(
//                topBar = {
//                    TopAppBar(
//                        title = {
//                            val title = if (addMemberToChatRoomConfirmationUIState.localChatRoom.chatRoom.subject != null) {
//                                "Invite new member to ${addMemberToChatRoomConfirmationUIState.localChatRoom.chatRoom.subject}"
//                            } else {
//                                "Invite new member to chat"
//                            }
//                            Text(
//                                text = title,
//                            )
//                        },
//                        actions = {
//                        }
//                    )
//                }
                bottomBar = {
                    BottomAppBar(
                        floatingActionButton = {
                            ExtendedFloatingActionButton(
                                onClick = {
                                    addMemberToChatRoomConfirmationViewModel.inviteToChatRoom(
                                        localChatRoom = addMemberToChatRoomConfirmationUIState.localChatRoom,
                                        peerPublicKey = addMemberToChatRoomConfirmationUIState.profile.publicKey,
                                        peerKeyPackage = addMemberToChatRoomConfirmationUIState.marmotKeyPackage,
                                        onInviteSent = onInviteSent
                                    )
                                }
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Invite new member"
                                )

                                Text("Invite ${addMemberToChatRoomConfirmationUIState.profile.humanReadableNameOrPubkey()}")
                            }
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
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(MaterialTheme.spacing.space250),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space125)
                    ) {
                        Spacer(
                            modifier = Modifier.weight(1f)
                        )

                        Text("Invite")

                        Text(
                            text = addMemberToChatRoomConfirmationUIState.profile.humanReadableNameOrPubkey()
                        )

                        Text(
                            text = "to join the ${addMemberToChatRoomConfirmationUIState.localChatRoom.chatRoom.subject ?: ""} chat room."
                        )

                        Text(
                            text = "Once invited will be able to receive and send private message sent to all the ${addMemberToChatRoomConfirmationUIState.localChatRoom.localParticipants.size} members in the chat room.",
                            textAlign = TextAlign.Center
                        )

                        Spacer(
                            modifier = Modifier.weight(3f)
                        )
                    }
                }
            }
        }
        AddMemberToChatRoomConfirmationUIState.Loading -> {
            Column(
                modifier = Modifier.fillMaxWidth().padding(
                    MaterialTheme.spacing.space250
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space250)
            ) {

                Spacer(
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Direct message detail",
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
            addMemberToChatRoomConfirmationViewModel.initiate()
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
                profilePublicKey = "hex",
                chatRoomId = "publicKey",
                relayHint = null,
                initialChatRoomDetailUIState =
//                    AddMemberToChatRoomConfirmationUIState.Error("Profile missing key package event.\n\nTry again later or tell Person to use Torch."),
                    AddMemberToChatRoomConfirmationUIState.Loaded(
                    localChatRoom = LocalChatRoom(
                        chatRoom = ChatRoom(
                            id = "hex",
                            userPublicKey = "hex",
                            subject = "Room title",
                            description = "See something... say something.",
                            initialGiftWrapPayloadId = "sdfaer",
                            mlsGroupState = null
                        ),
                    ),
                        profile = Profile(
                            publicKey = "hex",
                            nostrEventId = "hex",
                            displayName = "Person"
                        ),
                        marmotKeyPackage = MarmotKeyPackage(
                            id = "keypackage",
                            publicKey = "hex",
                            tlsEncodedMarmotKeyPackage = ByteArray(0)
                        )
                ),
                nostrRepository = NostrRepository.NO_OP_NOSTR_REPOSITORY,
                chatRepository = ChatRepository.NO_OP_CHAT_REPOSITORY,
                onInviteSent = {}
            )
        }
    }
}