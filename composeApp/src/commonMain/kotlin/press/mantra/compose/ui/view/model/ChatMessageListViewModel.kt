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
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
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
import press.mantra.compose.database.model.Participant
import press.mantra.compose.database.model.intermdiate.LocalChatMessage
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.database.model.types.SynchronizationFilter
import press.mantra.compose.extensions.shortened
import press.mantra.compose.extensions.toFormattedTimeAndDateString
import press.mantra.compose.nostr.Nip17Filters
import press.mantra.compose.nostr.Relays
import press.mantra.compose.repository.ChatRepository
import press.mantra.compose.repository.NostrRepository
import press.mantra.compose.ui.composable.widgets.profile.ProfileColor
import press.mantra.compose.ui.view.state.ChatMessageListUIState
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
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

    /**
     * The member the next message goes to privately, or null to send it to the room.
     *
     * Held here rather than on the composer because it survives recomposition and is read
     * by both the input and the send call. It is cleared on send: a private message is a
     * deliberate act each time, and leaving the composer armed after one is how somebody
     * sends the room's next message to one person, or one person's to the room.
     */
    var directMessageRecipient: Participant? by mutableStateOf(null)
        private set

    /** The message whose actions are open, or null. One at a time. */
    var openMessageActionsFor: Long? by mutableStateOf(null)
        private set

    fun startDirectMessage(participant: Participant) {
        logger.d("Arming a direct message to ${participant.participantPublicKey}")
        directMessageRecipient = participant
        openMessageActionsFor = null
    }

    fun cancelDirectMessage() {
        directMessageRecipient = null
    }

    fun openMessageActions(chatMessageId: Long?) {
        openMessageActionsFor = chatMessageId
    }

    /** The member behind a pubkey, if they are in this room. */
    fun participantFor(publicKey: HexKey?): Participant? =
        publicKey?.let { key ->
            localChatRoom.localParticipants
                .map { it.participant }
                .firstOrNull { it.participantPublicKey == key }
        }

    /**
     * What to call somebody, for a line that has to name them.
     *
     * Falls back to the key itself rather than to "unknown": a direct message names a
     * real member of this room, and a line that cannot say which one is worse than an
     * ugly one.
     */
    fun nameFor(publicKey: HexKey?): String {
        if (publicKey == null) return "someone"

        return localChatRoom.localParticipants
            .firstOrNull { it.participant.participantPublicKey == publicKey }
            ?.profile
            ?.humanReadableNameOrPubkey()
            ?: publicKey.shortened()
    }

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

    /**
     * Finds out where this room's participants read their messages.
     *
     * This used to schedule the room's message sync as well — gift wraps for a NIP-17 room,
     * group events for an MLS one. Both are now covered by the subscriptions
     * LiveSubscriptionManager holds open for the whole account, so opening a chat no longer
     * asks for its messages; they are already arriving.
     *
     * What is left is discovery, not message sync: if we do not hold a participant's
     * kind-10050 we cannot address a message to them, and that is worth resolving the moment
     * a chat is opened rather than whenever a background pass gets to it.
     */
    fun scheduleSynchronization() {
        logger.d("scheduleSynchronization")

        viewModelScope.launch(Dispatchers.IO) {
            if (localChatRoom.chatRoom.mlsGroupState == null) {
                localChatRoom.localParticipants.filter { it.participant.participantPublicKey != localChatRoom.chatRoom.userPublicKey }.forEach { recipients ->

                    val chatMessageRelayListEvent = chatRepository.getChatMessageRelayForPublicKey(recipients.participant.participantPublicKey)

                    // Opening a conversation used to refresh our own inbox here, with
                    // Nip17Filters.inbox on our own DM relays. That is now redundant rather
                    // than wrong: LiveSubscriptionManager holds exactly that filter open for
                    // the whole account and reconciles it on every foreground, so there is
                    // nothing left for opening a chat to ask for.
                    if (chatMessageRelayListEvent != null) return@forEach

                    isReceiverChatMessageRelayListMissing.value = true

                    val chatMessageRelayListFilter = SynchronizationFilter(
                        kinds = arrayOf(
                            ChatMessageRelayListEvent.KIND,
                        ),
                        authors = arrayOf(
                            recipients.participant.participantPublicKey
                        ),
                        limit = 50
                    )

                    val negentropySynchronizeRequests = Relays.DefaultDMRelayList.map { normalizedRelayUrl ->
                        NegentropySynchronizeRequest(
                            id = NegentropySynchronizeRequest.computeId(
                                relayURL = normalizedRelayUrl.url,
                                synchronizationFilter = chatMessageRelayListFilter
                            ),
                            // Only one case reaches here now, so the purpose is no longer a
                            // question.
                            purpose = "chat-message-relays",
                            synchronizationFilter = chatMessageRelayListFilter,
                            relayURL = normalizedRelayUrl.url,
                            level = 0
                        )
                    }
                    logger.i("negentropySynchronizeRequests: $negentropySynchronizeRequests")

                    nostrRepository.queueNegentropySynchronizeRequest(
                        negentropySynchronizeRequests
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun RenderMessages(onOpenSharedKey: () -> Unit, onOpenSigning: () -> Unit) {

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

                            // Which request lines are still asking something of the
                            // reader. Read off the transcript rather than the session
                            // -- the rows are what a line rendered days later has --
                            // and both rules live on ChatMessage, where they can be
                            // stated once and tested.
                            val messages = chatRoomDetailMessageListUIState
                                .chatMessageList
                                .map { it.chatMessage }

                            val answeredRequests = ChatMessage.answeredRequests(messages)
                            val settledRequests = ChatMessage.settledRequests(messages)

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
                                            isSettled = localChatMessage.chatMessage.id in settledRequests,
                                            onClick = onOpenSharedKey
                                        )
                                        return@items
                                    }

                                    // A private message this device cannot open. Everything
                                    // about it is known except the one thing that matters,
                                    // so it is a notice rather than an empty bubble --
                                    // which would read as the sender having said nothing.
                                    if (localChatMessage.chatMessage.messageType == ChatMessage.TYPE_DIRECT_MESSAGE &&
                                        localChatMessage.chatMessage.content.isBlank()
                                    ) {
                                        PrivateMessageNotice(
                                            sender = nameFor(localChatMessage.chatMessage.senderPublicKey),
                                            recipient = nameFor(localChatMessage.chatMessage.directMessageRecipientPublicKey)
                                        )
                                        return@items
                                    }

                                    // Signing lines are the same kind of thing and get
                                    // the same treatment -- nobody said them either --
                                    // but they lead somewhere else, because what a
                                    // reader needs from one is the event being signed
                                    // rather than the state of the key.
                                    if (localChatMessage.chatMessage.messageType in ChatMessage.FROST_TYPES) {
                                        RitualNotice(
                                            localChatMessage = localChatMessage,
                                            isAnswered = localChatMessage.chatMessage.id in answeredRequests,
                                            isSettled = localChatMessage.chatMessage.id in settledRequests,
                                            onClick = onOpenSigning
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
                                            // Only somebody else's message, and only in a
                                            // group -- a NIP-17 room has no audience for a
                                            // message to be private from.
                                            val canReplyPrivately =
                                                localChatRoom.chatRoom.mlsGroupState != null &&
                                                    !localChatMessage.chatMessage.isUserMessage &&
                                                    participantFor(localChatMessage.chatMessage.senderPublicKey) != null

                                            Card(
                                                modifier = Modifier.widthIn(
                                                    max = screenWidth * 0.8f
                                                ).wrapContentWidth(),
                                                onClick = {
                                                    if (canReplyPrivately) {
                                                        openMessageActions(localChatMessage.chatMessage.id)
                                                    }
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

                                                    // A readable direct message must never
                                                    // pass for a public one. The label says
                                                    // who the other party is, since that is
                                                    // the thing a reader would otherwise
                                                    // assume was the whole room.
                                                    if (localChatMessage.chatMessage.messageType == ChatMessage.TYPE_DIRECT_MESSAGE) {
                                                        Row(
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Icon(
                                                                Icons.Default.Lock,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(12.dp),
                                                                tint = MaterialTheme.colorScheme.primary
                                                            )
                                                            Text(
                                                                text = if (localChatMessage.chatMessage.isUserMessage) {
                                                                    "Private to ${nameFor(localChatMessage.chatMessage.directMessageRecipientPublicKey)}"
                                                                } else {
                                                                    "Private to you"
                                                                },
                                                                color = MaterialTheme.colorScheme.primary,
                                                                style = MaterialTheme.typography.labelSmall
                                                            )
                                                        }
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

                                            DropdownMenu(
                                                expanded = openMessageActionsFor == localChatMessage.chatMessage.id,
                                                onDismissRequest = { openMessageActions(null) }
                                            ) {
                                                DropdownMenuItem(
                                                    text = {
                                                        Text("Reply privately to ${nameFor(localChatMessage.chatMessage.senderPublicKey)}")
                                                    },
                                                    leadingIcon = {
                                                        Icon(Icons.Default.Lock, contentDescription = null)
                                                    },
                                                    onClick = {
                                                        participantFor(localChatMessage.chatMessage.senderPublicKey)
                                                            ?.let { startDirectMessage(it) }
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
            // Read and cleared together with the text, before the send suspends, so a
            // second message cannot inherit the first one's audience.
            val recipient = directMessageRecipient
            textFieldState.clearText()
            directMessageRecipient = null

            viewModelScope.launch(Dispatchers.IO) {
                chatRepository.sendChatMessage(
                    text = text,
                    localChatRoom = localChatRoom,
                    directMessageRecipientPublicKey = recipient?.participantPublicKey
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
 * A private message this device cannot read, as a system line.
 *
 * Deliberately not a bubble. The group is meant to know that a private message was sent
 * and to whom -- that is the honest half of the feature -- but an empty bubble attributed
 * to the sender would read as them having said nothing, and a bubble with placeholder text
 * would read as them having said the placeholder.
 *
 * Not tappable: there is nothing behind it to open. See docs/marmot-direct-messages.md.
 */
@Composable
private fun PrivateMessageNotice(
    sender: String,
    recipient: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = "$sender sent a private message to $recipient",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall
        )
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
    isSettled: Boolean,
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

        ChatMessage.TYPE_FROST_STARTED -> Icons.Default.Draw
        ChatMessage.TYPE_FROST_NONCE -> Icons.Default.Upload
        ChatMessage.TYPE_FROST_SIGNER_SET -> Icons.Default.Groups
        ChatMessage.TYPE_FROST_PARTIAL_SIGNATURE -> Icons.Default.Draw
        ChatMessage.TYPE_FROST_SIGNATURE -> Icons.Default.WorkspacePremium
        ChatMessage.TYPE_FROST_COMPLETE -> Icons.Default.CheckCircle
        ChatMessage.TYPE_FROST_FAILED -> Icons.Default.ErrorOutline
        ChatMessage.TYPE_FROST_APPROVAL_NEEDED -> Icons.Default.Draw

        else -> Icons.Default.PanTool
    }

    // The requests are the ritual lines that ask rather than report, and the ones
    // the ceremony cannot get past on its own. Everything else here is deliberately
    // quiet; these are not.
    // An answered request is history, not a summons: it keeps its stage's icon so
    // the step is still recognisable, but drops the colour and the call to action.
    // So is a settled one -- declined, or signed by a quorum that did not need this
    // member. Nothing was answered there, so it gets no tick, but offering to
    // review it would be offering a decision that has already gone by.
    val isRequest = (
        chatMessage.messageType in ChatMessage.DKG_REQUEST_TYPES ||
            chatMessage.messageType in ChatMessage.FROST_REQUEST_TYPES
        ) && !isAnswered && !isSettled

    val tint = when {
        chatMessage.messageType == ChatMessage.TYPE_DKG_FAILED ||
            chatMessage.messageType == ChatMessage.TYPE_FROST_FAILED ->
            MaterialTheme.colorScheme.error
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
                    if (chatMessage.messageType in ChatMessage.DKG_AUTHORED_TYPES ||
                        chatMessage.messageType in ChatMessage.FROST_AUTHORED_TYPES
                    ) {
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
