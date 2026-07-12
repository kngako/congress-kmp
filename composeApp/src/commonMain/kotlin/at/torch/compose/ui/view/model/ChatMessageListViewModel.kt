package at.torch.compose.ui.view.model

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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyOff
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
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
import at.torch.compose.database.model.NegentropySynchronizeRequest
import at.torch.compose.database.model.intermdiate.LocalChatRoom
import at.torch.compose.database.model.types.SynchronizationFilter
import at.torch.compose.extensions.toFormattedTimeAndDateString
import at.torch.compose.nostr.Relays
import at.torch.compose.repository.ChatRepository
import at.torch.compose.repository.NostrRepository
import at.torch.compose.ui.composable.widgets.profile.ProfileColor
import at.torch.compose.ui.view.state.ChatMessageListUIState
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEvent
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class ChatMessageListViewModel(
    initialChatMessageListUIState: ChatMessageListUIState,
    val localChatRoom: LocalChatRoom,
    val nostrRepository: NostrRepository,
    val chatRepository: ChatRepository
): ViewModel() {
    val logger = Logger.withTag(TAG)
    var chatMessageListUIState: ChatMessageListUIState by mutableStateOf(initialChatMessageListUIState)
        private set

    val isReceiverChatMessageRelayListMissing: MutableState<Boolean> = mutableStateOf(false)

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
                chatMessageListUIState = ChatMessageListUIState.Loaded(
                    chatMessageList = chatMessages
                )
            }
        }
    }

    fun scheduleSynchronization() {
        logger.d("scheduleSynchronization")

        viewModelScope.launch(Dispatchers.IO) {
            // Sync Notifications... might want to also run this in the background
            if (localChatRoom.chatRoom.mlsGroupState == null) {
                localChatRoom.localParticipants.filter { it.participant.participantPublicKey != localChatRoom.chatRoom.userPublicKey }.forEach { recipients ->

                    val chatMessageRelayListEvent = chatRepository.getChatMessageRelayForPublicKey(recipients.participant.participantPublicKey)

                    val relayAndSynchronizationFilter = if (chatMessageRelayListEvent != null) {
                        // Sync messages from this relay...
                        Pair(
                            chatMessageRelayListEvent.relays(),
                            SynchronizationFilter(
                                kinds = arrayOf(
                                    GiftWrapEvent.KIND,
                                ),
                                tags = mapOf(
                                    Pair("p", listOf(recipients.participant.participantPublicKey))
                                ),
                                limit = 50
                            )
                        )

                    } else {
                        // Try to find the ChatMessageRelayList for this participant...
                        isReceiverChatMessageRelayListMissing.value = true
                        // TODO: compute the timestamp for when list we sent messages and use that as since...
                        Pair(
                            Relays.DefaultDMRelayList,
                            SynchronizationFilter(
                                kinds = arrayOf(
                                    ChatMessageRelayListEvent.KIND,
                                ),
                                authors = arrayOf(
                                    recipients.participant.participantPublicKey
                                ),
                                limit = 50
                            )
                        )
                    }

                    logger.i("GiftWrapFilter: ${relayAndSynchronizationFilter.second}")

                    val negentropySynchronizeRequests = relayAndSynchronizationFilter.first.map { normalizedRelayUrl ->
                        NegentropySynchronizeRequest(
                            id = NegentropySynchronizeRequest.computeId(
                                relayURL = normalizedRelayUrl.url,
                                synchronizationFilter = relayAndSynchronizationFilter.second
                            ),
                            purpose = if (isReceiverChatMessageRelayListMissing.value) {
                                "chat-message-relays"
                            } else {
                                "sent-messages"
                            },
                            synchronizationFilter = relayAndSynchronizationFilter.second,
                            relayURL = normalizedRelayUrl.url,
                            level = 0
                        )
                    }
                    logger.i("negentropySynchronizeRequests: $negentropySynchronizeRequests")

                    nostrRepository.queueNegentropySynchronizeRequest(
                        negentropySynchronizeRequests
                    )
                }
            } else {
                // Sync mlsMessages
                val relayChatRoomMaps = Relays.DefaultDMRelayList.map { dmRelay ->
                    dmRelay.url to listOf(
                        localChatRoom.chatRoom.id
                    )
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
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun RenderMessages() {

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (val chatRoomDetailMessageListUIState = this@ChatMessageListViewModel.chatMessageListUIState) {
                ChatMessageListUIState.Error -> {
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
                is ChatMessageListUIState.Loaded -> {
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
                                item {
                                    if (isReceiverChatMessageRelayListMissing.value) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Card(
                                                onClick = {
                                                    // TODO: Open description for chatMessageRelayList
                                                },
                                                modifier = Modifier.fillMaxWidth(0.79f),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                                )
                                            ) {

                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                                                    horizontalArrangement = Arrangement.Center,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Text(
                                                            text = "No Chat Message Relays were found for this user.",
                                                            textAlign = TextAlign.Center,
                                                            style = MaterialTheme.typography.labelSmall
                                                        )
                                                    }

                                                    Icon(
                                                        Icons.Default.Info,
                                                        contentDescription = "Warning description"
                                                    )
                                                }


                                            }
                                        }
                                    }
                                }
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
                ChatMessageListUIState.Loading -> {
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
            initialChatMessageListUIState: ChatMessageListUIState = ChatMessageListUIState.Loading,
            localChatRoom: LocalChatRoom,
            nostrRepository: NostrRepository,
            chatRepository: ChatRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ChatMessageListViewModel(
                    initialChatMessageListUIState = initialChatMessageListUIState,
                    localChatRoom = localChatRoom,
                    nostrRepository = nostrRepository,
                    chatRepository = chatRepository
                )
            }
        }
    }
}