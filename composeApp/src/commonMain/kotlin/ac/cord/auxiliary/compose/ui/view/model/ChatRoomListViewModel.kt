package ac.cord.auxiliary.compose.ui.view.model

import ac.cord.auxiliary.compose.database.model.NegentropySynchronizeRequest
import ac.cord.auxiliary.compose.database.model.Profile
import ac.cord.auxiliary.compose.database.model.types.SynchronizationFilter
import ac.cord.auxiliary.compose.nostr.Relays
import ac.cord.auxiliary.compose.repository.ChatRepository
import ac.cord.auxiliary.compose.repository.NostrRepository
import ac.cord.auxiliary.compose.ui.composable.navigation.routes.ChatRoomDetailRoute
import ac.cord.auxiliary.compose.ui.composable.navigation.routes.Route
import ac.cord.auxiliary.compose.ui.composable.widgets.profile.ProfileColor
import ac.cord.auxiliary.compose.ui.view.state.ChatRoomListUIState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class ChatRoomListViewModel(
    initialChatRoomListUIState: ChatRoomListUIState,
    val publicKey: String,
    val synchronizationFilter: SynchronizationFilter,
    val nostrRepository: NostrRepository,
    val chatRepository: ChatRepository
): ViewModel() {
    val logger = Logger.withTag(TAG)
    var chatRoomListUIState: ChatRoomListUIState by mutableStateOf(initialChatRoomListUIState)
        private set

    fun initiate() {
        logger.d("init")
        scheduleSynchronization()
        observeChatRoomFeed()
    }

    fun observeChatRoomFeed() {
        logger.d("observeChatRoomFeed")
        viewModelScope.launch(Dispatchers.IO) {
            chatRepository.getChatRoomListByPublicKey(
                publicKey
            ).let { chatRooms ->
                logger.d("ChatRoomList: $chatRooms")
                chatRoomListUIState = ChatRoomListUIState.Loaded(
                    chatRoomList = chatRooms
                )
            }
        }
    }

    fun scheduleSynchronization() {
        logger.d("scheduleSynchronization")
        viewModelScope.launch(Dispatchers.IO) {
            // Sync Notifications... might want to also run this in the background
            nostrRepository.queueNegentropySynchronizeRequest(
                Relays.DefaultDMRelayList.shuffled().map { normalizedRelayUrl ->
                    NegentropySynchronizeRequest(
                        id = NegentropySynchronizeRequest.computeId(
                            relayURL = normalizedRelayUrl.url,
                            synchronizationFilter = synchronizationFilter
                        ),
                        purpose = "chat",
                        synchronizationFilter = synchronizationFilter,
                        relayURL = normalizedRelayUrl.url,
                        level = 0
                    )
                }
            )
        }
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun RenderFeed(
        onNavigateToDirectMessageDetail: (Route) -> Unit
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (val chatRoomListUIState = chatRoomListUIState) {
                ChatRoomListUIState.Error -> {
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
                is ChatRoomListUIState.Loaded -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {

                        if (chatRoomListUIState.chatRoomList.isEmpty()) {
                            Text(
                                text = "No messages. Go to a profile and send them a message."
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(
                                    items = chatRoomListUIState.chatRoomList,
                                    key = { it.chatRoom.id }
                                ) { localChatRoom ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = {
                                            onNavigateToDirectMessageDetail.invoke(
                                                ChatRoomDetailRoute(
                                                    directMessageIdentifier = localChatRoom.chatRoom.id
                                                )
                                            )
                                        }
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(20.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            localChatRoom.RenderChatRoomTitleText()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                ChatRoomListUIState.Loading -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    )  {
                        Spacer(
                            modifier = Modifier.height(50.dp)
                        )
                        Text(
                            text = "Loading",
                            textAlign = TextAlign.Center
                        )
                        Spacer(
                            modifier = Modifier.height(50.dp)
                        )

                        LoadingIndicator()
                    }
                }
            }
        }
    }

    companion object {
        const val TAG = "ChatRoomListViewModel"

        fun factory(
            initialChatRoomListUIState: ChatRoomListUIState = ChatRoomListUIState.Loading,
            publicKey: String,
            synchronizationFilter: SynchronizationFilter,
            nostrRepository: NostrRepository,
            chatRepository: ChatRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ChatRoomListViewModel(
                    initialChatRoomListUIState = initialChatRoomListUIState,
                    publicKey = publicKey,
                    synchronizationFilter = synchronizationFilter,
                    nostrRepository = nostrRepository,
                    chatRepository = chatRepository
                )
            }
        }
    }
}