package press.mantra.compose.ui.view.model

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import press.mantra.compose.database.model.ChatMessage
import press.mantra.compose.database.model.NegentropySynchronizeRequest
import press.mantra.compose.database.model.intermdiate.LocalChatMessage
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.database.model.types.SynchronizationFilter
import press.mantra.compose.extensions.shortened
import press.mantra.compose.extensions.toFormattedTimeAndDateString
import press.mantra.compose.nostr.Relays
import press.mantra.compose.repository.ChatRepository
import press.mantra.compose.repository.NostrRepository
import press.mantra.compose.ui.composable.widgets.profile.ProfileColor
import press.mantra.compose.ui.view.state.ChatMessageListUIState
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
    fun RenderMessages(onOpenSharedKey: () -> Unit) {

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

                            // A request line is answered when the step it asked for
                            // has since been published by this device -- which is
                            // exactly what approving it does. The transcript already
                            // records that as an authored line, so the answer is here
                            // in the list rather than in the session, and it stays
                            // right for a room that has run more than one ceremony.
                            val answeredRequests = chatRoomDetailMessageListUIState
                                .chatMessageList
                                .mapNotNull { request ->
                                    val published = ChatMessage
                                        .DKG_REQUEST_FULFILMENTS[request.chatMessage.messageType]
                                        ?: return@mapNotNull null

                                    val done = chatRoomDetailMessageListUIState.chatMessageList.any {
                                        it.chatMessage.messageType == published &&
                                            it.chatMessage.isUserMessage &&
                                            it.chatMessage.createdAt >= request.chatMessage.createdAt
                                    }

                                    request.chatMessage.id.takeIf { done }
                                }
                                .toSet()

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
                                    // Not somebody's words -- see ChatMessage.DKG_TYPES.
                                    // A bubble would attribute "a shared key ceremony
                                    // started" to the coordinator as if they had said it.
                                    if (localChatMessage.chatMessage.messageType in ChatMessage.DKG_TYPES) {
                                        RitualNotice(
                                            localChatMessage = localChatMessage,
                                            isAnswered = localChatMessage.chatMessage.id in answeredRequests,
                                            onClick = onOpenSharedKey
                                        )
                                        return@items
                                    }

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

                                                    SelectionContainer {

                                                        Text(
                                                            text = localChatMessage.chatMessage.content,
                                                            style = MaterialTheme.typography.bodyMedium
                                                        )

                                                    }

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
        const val TAG = "ChatMessageListViewModel"

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

/**
 * A ChillDKG milestone, as a system line across the transcript.
 *
 * Deliberately not a bubble: nobody said this, and giving it a sender and a side
 * would make the coordinator appear to have announced it. It is tappable because
 * the point of telling the group is to give them somewhere to go — a ritual only
 * finishes once every member's device has taken part, and the ladder that shows
 * who it is waiting on lives on the shared-key screen.
 */
@Composable
private fun RitualNotice(
    localChatMessage: LocalChatMessage,
    isAnswered: Boolean,
    onClick: () -> Unit,
) {
    val chatMessage = localChatMessage.chatMessage

    // One per stage. A ceremony puts a dozen-odd lines in a row into the transcript,
    // and with a single icon on all of them the reader has to actually read each to
    // tell "somebody joined" from "somebody contributed" from "you are being asked
    // for something". A request shares its stage's icon rather than getting a
    // distinct one: it is the same step, before rather than after, and the primary
    // tint and the Review affordance already say which.
    val icon = when (chatMessage.messageType) {
        ChatMessage.TYPE_DKG_STARTED -> Icons.Default.Key
        ChatMessage.TYPE_DKG_HOST_KEY -> Icons.Default.PersonAdd
        ChatMessage.TYPE_DKG_ROUND_1 -> Icons.Default.Upload
        ChatMessage.TYPE_DKG_COORDINATOR_ROUND_1 -> Icons.Default.CallMerge
        ChatMessage.TYPE_DKG_ROUND_2 -> Icons.Default.FactCheck
        ChatMessage.TYPE_DKG_CERTIFICATE -> Icons.Default.WorkspacePremium
        ChatMessage.TYPE_DKG_COMPLETE -> Icons.Default.CheckCircle
        ChatMessage.TYPE_DKG_FAILED -> Icons.Default.ErrorOutline

        ChatMessage.TYPE_DKG_APPROVAL_NEEDED_HOST_KEY -> Icons.Default.PersonAdd
        ChatMessage.TYPE_DKG_APPROVAL_NEEDED_ROUND_1 -> Icons.Default.Upload
        ChatMessage.TYPE_DKG_APPROVAL_NEEDED_ROUND_2 -> Icons.Default.FactCheck

        else -> Icons.Default.PanTool
    }

    // The requests are the ritual lines that ask rather than report, and the ones
    // the ceremony cannot get past on its own. Everything else here is deliberately
    // quiet; these are not.
    // An answered request is history, not a summons: it keeps its stage's icon so
    // the step is still recognisable, but drops the colour and the call to action.
    val isRequest = chatMessage.messageType in ChatMessage.DKG_REQUEST_TYPES && !isAnswered

    val tint = when {
        chatMessage.messageType == ChatMessage.TYPE_DKG_FAILED -> MaterialTheme.colorScheme.error
        isRequest -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = buildAnnotatedString {
                    // Somebody opened this ceremony, or somebody walked away from it,
                    // and which member that was is the point of the line. Resolved
                    // from the joined profile rather than written into the content,
                    // so it follows a rename and is not stuck on the "LOADING..."
                    // placeholder a member is given the moment they are first seen.
                    if (chatMessage.messageType in ChatMessage.DKG_AUTHORED_TYPES) {
                        withStyle(
                            SpanStyle(color = ProfileColor.fromPublicKey(chatMessage.senderPublicKey))
                        ) {
                            append(
                                if (chatMessage.isUserMessage) {
                                    "You"
                                } else {
                                    localChatMessage.profile?.humanReadableNameOrPubkey()
                                        ?: chatMessage.senderPublicKey.shortened()
                                }
                            )
                        }
                        append(" ")
                    }

                    append(chatMessage.content)
                },
                style = MaterialTheme.typography.bodySmall,
                color = tint
            )

            Text(
                text = chatMessage.createdAt.toFormattedTimeAndDateString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (isRequest) {
            Text(
                text = "Review",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        } else if (isAnswered) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "You approved this",
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "Open the shared key ceremony",
            tint = tint
        )
    }
}
