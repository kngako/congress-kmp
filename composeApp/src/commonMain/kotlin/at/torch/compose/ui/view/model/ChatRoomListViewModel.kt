package at.torch.compose.ui.view.model

import at.torch.compose.database.model.NegentropySynchronizeRequest
import at.torch.compose.ui.composable.navigation.routes.ChatRoomDetailRoute
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

class ChatRoomListViewModel(
    initialChatRoomListUIState: at.torch.compose.ui.view.state.ChatRoomListUIState,
    val publicKey: String,
    val synchronizationFilter: at.torch.compose.database.model.types.SynchronizationFilter,
    val nostrRepository: at.torch.compose.repository.NostrRepository,
    val chatRepository: at.torch.compose.repository.ChatRepository
): ViewModel() {
    val logger = Logger.withTag(TAG)
    var chatRoomListUIState: at.torch.compose.ui.view.state.ChatRoomListUIState by mutableStateOf(initialChatRoomListUIState)
        private set

    fun initiate() {
        logger.d("init")
        scheduleSynchronization()
        observeChatRoomFeed()
    }

    fun observeChatRoomFeed() {
        logger.d("observeChatRoomFeed")
        viewModelScope.launch(Dispatchers.IO) {
            chatRepository.observeChatRoomListByPublicKey(
                publicKey
            ).collect { chatRooms ->
                logger.d("ChatRoomList: $chatRooms")
                chatRoomListUIState = _root_ide_package_.at.torch.compose.ui.view.state.ChatRoomListUIState.Loaded(
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
                _root_ide_package_.at.torch.compose.nostr.Relays.DefaultDMRelayList.shuffled().map { normalizedRelayUrl ->
                    _root_ide_package_.ac.cord.compose.database.model.NegentropySynchronizeRequest(
                        id = _root_ide_package_.at.torch.compose.database.model.NegentropySynchronizeRequest.Companion.computeId(
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
        onNavigateToDirectMessageDetail: (at.torch.compose.ui.composable.navigation.routes.Route) -> Unit
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (val chatRoomListUIState = chatRoomListUIState) {
                _root_ide_package_.at.torch.compose.ui.view.state.ChatRoomListUIState.Error -> {
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
                is at.torch.compose.ui.view.state.ChatRoomListUIState.Loaded -> {
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
                                                _root_ide_package_.ac.cord.compose.ui.composable.navigation.routes.ChatRoomDetailRoute(
                                                    activeUserPublicKey = publicKey,
                                                    directMessageIdentifier = localChatRoom.chatRoom.id,
                                                    relayHint = localChatRoom.localParticipants.firstOrNull { it.participant.participantPublicKey != localChatRoom.chatRoom.userPublicKey }?.participant?.relayHint
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
                _root_ide_package_.at.torch.compose.ui.view.state.ChatRoomListUIState.Loading -> {
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
            initialChatRoomListUIState: at.torch.compose.ui.view.state.ChatRoomListUIState = _root_ide_package_.at.torch.compose.ui.view.state.ChatRoomListUIState.Loading,
            publicKey: String,
            synchronizationFilter: at.torch.compose.database.model.types.SynchronizationFilter,
            nostrRepository: at.torch.compose.repository.NostrRepository,
            chatRepository: at.torch.compose.repository.ChatRepository
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