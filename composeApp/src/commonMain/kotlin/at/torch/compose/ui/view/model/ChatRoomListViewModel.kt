package at.torch.compose.ui.view.model

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
import at.torch.compose.database.model.NegentropySynchronizeRequest
import at.torch.compose.database.model.types.SynchronizationFilter
import at.torch.compose.nostr.Relays
import at.torch.compose.repository.ChatRepository
import at.torch.compose.repository.NostrRepository
import at.torch.compose.ui.view.state.ChatRoomListUIState
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

class ChatRoomListViewModel(
    initialChatRoomListUIState: ChatRoomListUIState,
    val publicKey: String,
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
            chatRepository.observeChatRoomListByPublicKey(
                publicKey
            ).collect { chatRooms ->
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
            val chatRequestFilter = SynchronizationFilter(
                kinds = arrayOf(
                    GiftWrapEvent.KIND,
                ),
                tags = mapOf(
                    Pair("p", listOf(publicKey))
                ),
                limit = 50
            )
            nostrRepository.queueNegentropySynchronizeRequest(
                Relays.DefaultDMRelayList.shuffled().map { normalizedRelayUrl ->
                    NegentropySynchronizeRequest(
                        id = NegentropySynchronizeRequest.computeId(
                            relayURL = normalizedRelayUrl.url,
                            synchronizationFilter = chatRequestFilter
                        ),
                        purpose = "chat",
                        synchronizationFilter = chatRequestFilter,
                        relayURL = normalizedRelayUrl.url,
                        level = 0
                    )
                }
            )

            // Get/observe groupIds for publicKey
            val chatRooms = chatRepository.getChatRoomListByPublicKey(publicKey)
            val relayChatRoomMaps = Relays.DefaultDMRelayList.map { dmRelay ->
                dmRelay.url to chatRooms.map { localChatRoom ->
                    localChatRoom.chatRoom.id
                }
            }

            val negentropySyncRequests = relayChatRoomMaps.map { relayChatRoomMap ->
                val mlsGroupMessageFilter = SynchronizationFilter(
                    kinds = arrayOf(GroupEvent.KIND),
                    tags = mapOf("h" to relayChatRoomMap.second),
                )
                logger.d("mlsGroupMessageFilter: $mlsGroupMessageFilter")

                NegentropySynchronizeRequest(
                    id = NegentropySynchronizeRequest.computeId(
                        relayURL = relayChatRoomMap.first,
                        synchronizationFilter = mlsGroupMessageFilter
                    ),
                    purpose = "mlsMessages",
                    synchronizationFilter = mlsGroupMessageFilter,
                    relayURL = relayChatRoomMap.first,
                    level = 0
                )
            }

            logger.d("negentropySyncRequests: ${negentropySyncRequests.map { it.synchronizationFilter }}")
            nostrRepository.queueNegentropySynchronizeRequest(
                negentropySyncRequests
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
                                                _root_ide_package_.at.torch.compose.ui.composable.navigation.routes.ChatRoomMessagingRoute(
                                                    activeUserPublicKey = publicKey,
                                                    chatRoomId = localChatRoom.chatRoom.id,
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
            nostrRepository: NostrRepository,
            chatRepository: ChatRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ChatRoomListViewModel(
                    initialChatRoomListUIState = initialChatRoomListUIState,
                    publicKey = publicKey,
                    nostrRepository = nostrRepository,
                    chatRepository = chatRepository
                )
            }
        }
    }
}