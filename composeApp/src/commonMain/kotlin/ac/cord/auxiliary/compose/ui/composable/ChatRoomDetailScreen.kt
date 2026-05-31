package ac.cord.auxiliary.compose.ui.composable

import ac.cord.auxiliary.compose.database.model.ChatRoom
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalChatRoom
import ac.cord.auxiliary.compose.repository.ChatRepository
import ac.cord.auxiliary.compose.repository.NostrRepository
import ac.cord.auxiliary.compose.ui.composable.widgets.LoadingDataIndicator
import ac.cord.auxiliary.compose.ui.theme.AuxTheme
import ac.cord.auxiliary.compose.ui.view.model.ChatRoomDetailViewModel
import ac.cord.auxiliary.compose.ui.view.state.ChatRoomDetailUIState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.time.Clock

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChatRoomDetailScreen(
    initialChatRoomDetailUIState: ChatRoomDetailUIState = ChatRoomDetailUIState.Loading,
    nostrRepository: NostrRepository,
    chatRepository: ChatRepository
) {
    val chatRoomDetailViewModel: ChatRoomDetailViewModel = viewModel(
        factory = ChatRoomDetailViewModel.factory(
            publicKey = "",
            initialChatRoomDetailUIState = initialChatRoomDetailUIState,
            nostrRepository = nostrRepository,
            chatRepository = chatRepository
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

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(chatRoomDetailUIState.localChatRoom.chatRoom.subject ?: "Message")
                        }
                    )
                },
                bottomBar = {
                    BottomAppBar {
                        Text("Message actions coming soon...")
                    }
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier.padding(innerPadding)
                ) {
                    Text("Messages will appear here...")
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
private fun UnqueuedProfileSynchronizationScreenPreview() {
    AuxTheme {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            ChatRoomDetailScreen(
                initialChatRoomDetailUIState = ChatRoomDetailUIState.Loaded(
                    localChatRoom = LocalChatRoom(
                        chatRoom = ChatRoom(
                            id = "",
                            userPublicKey = "",
                            subject = "sdfa",
                            initialGiftWrapUnsignedId = ""
                        )
                    )
                ),
                nostrRepository = NostrRepository.NO_OP_NOSTR_REPOSITORY,
                chatRepository = ChatRepository.NO_OP_CHAT_REPOSITORY
            )
        }
    }
}