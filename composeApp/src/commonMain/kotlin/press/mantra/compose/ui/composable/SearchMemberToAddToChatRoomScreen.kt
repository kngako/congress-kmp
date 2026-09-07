package press.mantra.compose.ui.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import press.mantra.compose.database.model.ChatRoom
import press.mantra.compose.database.model.Profile
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.repository.ChatRepository
import press.mantra.compose.repository.NostrRepository
import press.mantra.compose.ui.composable.navigation.routes.AddMemberToChatRoomConfirmationRoute
import press.mantra.compose.ui.composable.navigation.routes.Route
import press.mantra.compose.ui.composable.widgets.LoadingDataIndicator
import press.mantra.compose.ui.composable.widgets.profile.ProfileAvatar
import press.mantra.compose.ui.theme.TorchTheme
import press.mantra.compose.ui.view.model.SearchMemberToAddToChatRoomViewModel
import press.mantra.compose.ui.view.state.SearchMemberToAddToChatRoomUIState
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import press.mantra.compose.ui.theme.spacing

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SearchMemberToAddToChatRoomScreen(
    activeUserPublicKey: HexKey,
    chatRoomId: String,
    relayHint: String?,
    initialSearchMemberToAddToChatRoomUIState: SearchMemberToAddToChatRoomUIState = SearchMemberToAddToChatRoomUIState.Loading,
    nostrRepository: NostrRepository,
    chatRepository: ChatRepository,
    onNavigateToRoute: (Route) -> Unit,
) {
    val searchMemberToAddToChatRoomViewModel: SearchMemberToAddToChatRoomViewModel = viewModel(
        factory = SearchMemberToAddToChatRoomViewModel.factory(
            chatRoomId = chatRoomId,
            relayHint = relayHint,
            initialSearchMemberToAddToChatRoomUIState = initialSearchMemberToAddToChatRoomUIState,
            nostrRepository = nostrRepository,
            chatRepository = chatRepository,
            activeUserPublicKey = activeUserPublicKey
        )
    )

    when (val searchMemberToAddToChatRoomUIState = searchMemberToAddToChatRoomViewModel.searchMemberToAddToChatRoomUIState) {
        is SearchMemberToAddToChatRoomUIState.Error -> {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(
                    modifier = Modifier.height(MaterialTheme.spacing.space600)
                )
                Text(
                    text = searchMemberToAddToChatRoomUIState.message,
                )
            }
        }
        is SearchMemberToAddToChatRoomUIState.Loaded -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            val title = if (searchMemberToAddToChatRoomUIState.localChatRoom.chatRoom.subject != null) {
                                "Invite new member to ${searchMemberToAddToChatRoomUIState.localChatRoom.chatRoom.subject}"
                            } else {
                                "Invite new member to chat"
                            }
                            Text(
                                text = title,
                            )
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

                        if (searchMemberToAddToChatRoomUIState.contacts.isEmpty()) {
                            Spacer(
                                modifier = Modifier.height(20.dp)
                            )
                            Text(
                                modifier = Modifier.padding(20.dp),
                                text = "Currently no contacts. Please search and chat with a few people.",
                                textAlign = TextAlign.Center
                            )

                            Spacer(
                                modifier = Modifier.height(20.dp)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.space50),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                reverseLayout = true
                            ) {

                                items(
                                    items = searchMemberToAddToChatRoomUIState.contacts,
                                    key = { it.publicKey }
                                ) { profile ->
                                    Card(
                                        onClick = {
                                            onNavigateToRoute.invoke(
                                                AddMemberToChatRoomConfirmationRoute(
                                                    activeUserPublicKey = activeUserPublicKey,
                                                    chatRoomId = chatRoomId,
                                                    profilePublicKey = profile.publicKey,
                                                    relayHint = relayHint,
                                                )
                                            )
                                        }
                                    ) {
                                        ListItem(
                                            leadingContent = {
                                                ProfileAvatar(
                                                    profile = profile
                                                )
                                            },
                                            headlineContent = {
                                                Text(
                                                    profile.humanReadableNameOrPubkey()
                                                )
                                            },
                                            supportingContent = {
                                                profile.about?.let {
                                                    Text(
                                                        text = it,
                                                        overflow = TextOverflow.Ellipsis,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        SearchMemberToAddToChatRoomUIState.Loading -> {
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
                    text = "Search member functionality",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Search message functionality will be here.",
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
        if (initialSearchMemberToAddToChatRoomUIState == SearchMemberToAddToChatRoomUIState.Loading) {
            searchMemberToAddToChatRoomViewModel.initiate()
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
            SearchMemberToAddToChatRoomScreen(
                activeUserPublicKey = "",
                chatRoomId = "publicKey",
                relayHint = null,
                initialSearchMemberToAddToChatRoomUIState = SearchMemberToAddToChatRoomUIState.Loaded(
                    localChatRoom = LocalChatRoom(
                        chatRoom = ChatRoom(
                            id = "",
                            userPublicKey = "",
                            subject = "Message title",
                            description = "See something... say something.",
                            initialGiftWrapPayloadId = "sdfaer",
                            mlsGroupState = null
                        ),
                    ),
                    contacts = listOf(
                        Profile(
                            publicKey = "hex",
                            displayName = "Man With A Plan",
                            about = "Making plans and getting things done...",
                            nostrEventId = "nostrEventId"
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