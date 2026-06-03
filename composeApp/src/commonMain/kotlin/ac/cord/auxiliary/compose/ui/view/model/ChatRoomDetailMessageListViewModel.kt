package ac.cord.auxiliary.compose.ui.view.model

import ac.cord.auxiliary.compose.database.model.intermdiate.LocalChatRoom
import ac.cord.auxiliary.compose.extensions.toFormattedTimeAndDateString
import ac.cord.auxiliary.compose.repository.ChatRepository
import ac.cord.auxiliary.compose.repository.NostrRepository
import ac.cord.auxiliary.compose.ui.composable.widgets.profile.ProfileColor
import ac.cord.auxiliary.compose.ui.view.state.ChatRoomDetailMessageListUIState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyOff
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

class ChatRoomDetailMessageListViewModel(
    initialChatRoomDetailMessageListUIState: ChatRoomDetailMessageListUIState,
    val localChatRoom: LocalChatRoom,
    val nostrRepository: NostrRepository,
    val chatRepository: ChatRepository
): ViewModel() {
    val logger = Logger.withTag(TAG)
    var chatRoomDetailMessageListUIState: ChatRoomDetailMessageListUIState by mutableStateOf(initialChatRoomDetailMessageListUIState)
        private set

    fun initiate() {
        logger.d("init")
        scheduleSynchronization()
        observeChatRoomFeed()
    }

    fun observeChatRoomFeed() {
        logger.d("observeChatRoomFeed")
        viewModelScope.launch(Dispatchers.IO) {
            chatRepository.observeChatMessageListByChatRoomId(
                localChatRoom.chatRoom.id
            ).distinctUntilChanged().collect { chatMessages ->
                logger.d("getChatMessageListByChatRoomId: $chatMessages")
                chatRoomDetailMessageListUIState = ChatRoomDetailMessageListUIState.Loaded(
                    chatMessageList = chatMessages
                )
            }
        }
    }

    fun scheduleSynchronization() {
        logger.d("scheduleSynchronization")
//        viewModelScope.launch(Dispatchers.IO) {
//            // Sync Notifications... might want to also run this in the background
//            nostrRepository.queueNegentropySynchronizeRequest(
//                Relays.DefaultDMRelayList.shuffled().map { normalizedRelayUrl ->
//                    NegentropySynchronizeRequest(
//                        id = NegentropySynchronizeRequest.computeId(
//                            relayURL = normalizedRelayUrl.url,
//                            synchronizationFilter = synchronizationFilter
//                        ),
//                        purpose = "chat",
//                        synchronizationFilter = synchronizationFilter,
//                        relayURL = normalizedRelayUrl.url,
//                        level = 0
//                    )
//                }
//            )
//        }
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun RenderMessages() {

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (val chatRoomDetailMessageListUIState = this@ChatRoomDetailMessageListViewModel.chatRoomDetailMessageListUIState) {
                ChatRoomDetailMessageListUIState.Error -> {
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
                is ChatRoomDetailMessageListUIState.Loaded -> {
                    Spacer(
                        modifier = Modifier.weight(1f)
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {

                        if (chatRoomDetailMessageListUIState.chatMessageList.isEmpty()) {
                            Spacer(
                                modifier = Modifier.height(20.dp)
                            )
                            Text(
                                modifier = Modifier.padding(20.dp),
                                text = "Currently no messages have been shared.\nBreak the ice.",
                                textAlign = TextAlign.Center
                            )

                            Spacer(
                                modifier = Modifier.height(20.dp)
                            )
                        } else {

                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().padding(5.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                reverseLayout = true
                            ) {
                                items(
                                    items = chatRoomDetailMessageListUIState.chatMessageList,
                                    key = { it.chatMessage.id }
                                ) { localChatMessage ->
                                    BoxWithConstraints(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        val screenWidth = maxWidth

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = if (localChatMessage.chatMessage.isUserMessage) {
                                                Arrangement.End
                                            } else {
                                                Arrangement.Start
                                            }
                                        ) {
                                            Card(
                                                modifier = Modifier.widthIn(
                                                    max = screenWidth * 0.8f
                                                ).wrapContentWidth(),
                                                onClick = {

                                                },
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(10.dp),
                                                    horizontalAlignment = if (localChatMessage.chatMessage.isUserMessage) {
                                                        Alignment.End
                                                    } else {
                                                        Alignment.Start
                                                    },
                                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    if (localChatMessage.chatMessage.isUserMessage.not()) {
                                                        Text(
                                                            text = localChatMessage.profile?.humanReadableNameOrPubkey() ?: localChatMessage.chatMessage.senderPublicKey,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.MiddleEllipsis,
                                                            color = ProfileColor.fromPublicKey(localChatMessage.chatMessage.senderPublicKey),
                                                            style = MaterialTheme.typography.labelSmall
                                                        )
                                                    }

                                                    Text(
                                                        text = localChatMessage.chatMessage.content,
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )

                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(
                                                            10.dp,
                                                            Alignment.End
                                                        ),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                    ) {
                                                        Text(
                                                            text = localChatMessage.chatMessage.createdAt.toFormattedTimeAndDateString(),
                                                            style = MaterialTheme.typography.labelSmall
                                                        )
                                                        if (localChatMessage.chatMessage.isUserMessage) {
                                                            // TODO: Add delivered and read receipt..
                                                            if (localChatMessage.chatMessageBroadcastNostrEventReceiptRelation != null) {
                                                                Icon(
                                                                    Icons.Default.Check,
                                                                    contentDescription = "Message sent"
                                                                )
                                                            } else if (localChatMessage.chatMessageBroadcastNostrEventRequestRelation != null) {
                                                                Icon(
                                                                    Icons.Default.AccessTime,
                                                                    contentDescription = "Message sent"
                                                                )
                                                            } else if (localChatMessage.chatMessageNostrEventRelation != null) {
                                                                Icon(
                                                                    Icons.Default.Pending,
                                                                    contentDescription = "Message signed and sealed status"
                                                                )
                                                            } else {
                                                                Icon(
                                                                    Icons.Default.KeyOff,
                                                                    contentDescription = "Unsealed message status"
                                                                )
                                                            }

                                                        }

                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                ChatRoomDetailMessageListUIState.Loading -> {
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

    fun sendMessage(textFieldState: TextFieldState) {
        if (textFieldState.text.isNotBlank()) {
            val text = textFieldState.text.toString()
            textFieldState.clearText()

            viewModelScope.launch(Dispatchers.IO) {
                chatRepository.sendChatMessage(
                    text = text,
                    localChatRoom = localChatRoom
                )
            }

        }
    }

    companion object {
        const val TAG = "ChatRoomDetailMessageListViewModel"

        fun factory(
            initialChatRoomDetailMessageListUIState: ChatRoomDetailMessageListUIState = ChatRoomDetailMessageListUIState.Loading,
            localChatRoom: LocalChatRoom,
            nostrRepository: NostrRepository,
            chatRepository: ChatRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ChatRoomDetailMessageListViewModel(
                    initialChatRoomDetailMessageListUIState = initialChatRoomDetailMessageListUIState,
                    localChatRoom = localChatRoom,
                    nostrRepository = nostrRepository,
                    chatRepository = chatRepository
                )
            }
        }
    }
}