package ac.cord.auxiliary.compose.ui.view.model

import ac.cord.auxiliary.compose.database.model.ChatRoom
import ac.cord.auxiliary.compose.database.model.NegentropySynchronizeRequest
import ac.cord.auxiliary.compose.database.model.types.SynchronizationFilter
import ac.cord.auxiliary.compose.nostr.Relays
import ac.cord.auxiliary.compose.repository.NostrRepository
import ac.cord.auxiliary.compose.ui.view.state.ChatRoomListUIState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.MutableState
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class ChatRoomListViewModel(
    initialChatRoomListUIState: ChatRoomListUIState,
    val publicKey: String,
    val synchronizationFilter: SynchronizationFilter,
    val nostrRepository: NostrRepository
): ViewModel() {
    val logger = Logger.withTag(TAG)
    var chatRoomListUIState: ChatRoomListUIState by mutableStateOf(initialChatRoomListUIState)
        private set

    val isSynchronizationPending: MutableState<Boolean> = mutableStateOf(false)

    fun initiate() {
        logger.d("init")
//        scheduleSynchronization()
        observeLocalNostrFeed()
//        observeSynchronizeNostrEventRequestByPurposeAndStatusCount()
    }
    fun observeLocalNostrFeed() {
        logger.d("observeLocalNostrFeed")
        viewModelScope.launch(Dispatchers.IO) {
            nostrRepository.observeProfileWithFollowing(
                publicKey
            ).distinctUntilChanged().collect {
                chatRoomListUIState = ChatRoomListUIState.Loaded(
                    chatRoomList = listOf(
                        ChatRoom(
                            id ="steveBikoId",
                            userPublicKey = "steveBikoId",
                            name = "Steve Biko",
                            subject = "BC: Systems",
                            initialGiftWrapUnsignedId = "",
                        ),
                        ChatRoom(
                            id = "chrisHaniId",
                            userPublicKey = "chrisHaniId",
                            name = "Chris Hani",
                            subject = "MK: Gym",
                            initialGiftWrapUnsignedId = "",
                        ),
                        ChatRoom(
                            id = "desmondTutuId",
                            userPublicKey = "desmondTutuId",
                            name = "Desmond Tutu",
                            subject = "TRC: Masses",
                            initialGiftWrapUnsignedId = "",
                        ),
                        ChatRoom(
                            id = "hipHopPantsulaId",
                            userPublicKey = "hipHopPantsulaId",
                            name = "Hip Hop Pantsula",
                            subject = "Motswako: Jabba",
                            initialGiftWrapUnsignedId = "",
                        )
                    )
                )
            }
        }
    }

    fun scheduleSynchronization() {
        logger.d("scheduleSynchronization")
        viewModelScope.launch(Dispatchers.IO) {
            // Sync Notifications... might want to also run this in the background
            nostrRepository.queueNegentropySynchronizeRequest(
                Relays.negentropicRelaySet.shuffled().map { normalizedRelayUrl ->
                    NegentropySynchronizeRequest(
                        id = NegentropySynchronizeRequest.computeId(
                            relayURL = normalizedRelayUrl.url,
                            synchronizationFilter = synchronizationFilter
                        ),
                        purpose = "following",
                        synchronizationFilter = synchronizationFilter,
                        relayURL = normalizedRelayUrl.url,
                        level = 0
                    )
                }
            )
        }
    }

    fun observeSynchronizeNostrEventRequestByPurposeAndStatusCount() {
        logger.d("observeSynchronizeNostrEventRequestByPurposeAndStatusCount")
        viewModelScope.launch(Dispatchers.IO) {
            nostrRepository.observeSynchronizeNostrEventRequestByPurposeAndStatusCount("feed").distinctUntilChanged().collect { count ->
                logger.d("Pending/Processing Synchronization Request: $count")
                isSynchronizationPending.value = count > 0
            }
        }
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun RenderFeed(
        onNavigateToDirectMessageDetail: (String) -> Unit
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
                                item {
                                    Text(
                                        text = "Messages"
                                    )
                                }

                                items(
                                    items = chatRoomListUIState.chatRoomList,
                                    key = { it.id }
                                ) { chatRoom ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = {
                                            onNavigateToDirectMessageDetail.invoke("")
                                        }
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(20.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            Text(
                                                text = chatRoom.name ?: chatRoom.subject ?: "Unnamed chat"
                                            )
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
        const val TAG = "MessageListViewModel"

        fun factory(
            initialChatRoomListUIState: ChatRoomListUIState = ChatRoomListUIState.Loading,
            publicKey: String,
            synchronizationFilter: SynchronizationFilter,
            nostrRepository: NostrRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ChatRoomListViewModel(
                    initialChatRoomListUIState = initialChatRoomListUIState,
                    publicKey = publicKey,
                    synchronizationFilter = synchronizationFilter,
                    nostrRepository = nostrRepository
                )
            }
        }
    }
}