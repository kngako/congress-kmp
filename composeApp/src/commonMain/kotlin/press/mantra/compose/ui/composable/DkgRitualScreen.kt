package press.mantra.compose.ui.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import press.mantra.compose.database.model.DkgSession
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.database.model.types.DkgRitualStage
import press.mantra.compose.repository.ChatRepository
import press.mantra.compose.repository.DkgRepository
import press.mantra.compose.ui.composable.widgets.LoadingDataIndicator
import press.mantra.compose.ui.theme.TorchTheme
import press.mantra.compose.ui.view.model.DkgRitualViewModel
import press.mantra.compose.ui.view.state.DkgRitualUIState
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import fr.acinq.phoenix.data.ActiveWallet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The shared-key ceremony: a ChillDKG ritual run across the group's NIP-17
 * chat, shown as a ladder of rounds so members can see it is moving and who it
 * is waiting on.
 *
 * A DKG needs *everyone* present, which is unusual for a chat feature and the
 * main thing this screen has to communicate — hence a count against every step
 * rather than an indeterminate spinner.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DkgRitualScreen(
    activeUserPublicKey: HexKey,
    chatRoomId: String,
    initialDkgRitualUIState: DkgRitualUIState = DkgRitualUIState.Loading,
    activeWalletStateFlow: StateFlow<ActiveWallet?>,
    chatRepository: ChatRepository,
    dkgRepository: DkgRepository,
) {
    val dkgRitualViewModel: DkgRitualViewModel = viewModel(
        factory = DkgRitualViewModel.factory(
            chatRoomId = chatRoomId,
            activeUserPublicKey = activeUserPublicKey,
            initialDkgRitualUIState = initialDkgRitualUIState,
            activeWalletStateFlow = activeWalletStateFlow,
            chatRepository = chatRepository,
            dkgRepository = dkgRepository
        )
    )

    when (val dkgRitualUIState = dkgRitualViewModel.dkgRitualUIState) {
        is DkgRitualUIState.Error -> {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(50.dp))
                Text(text = dkgRitualUIState.message, textAlign = TextAlign.Center)
            }
        }
        is DkgRitualUIState.Loaded -> {
            val session = dkgRitualUIState.session
            val participantCount = dkgRitualUIState.participantCount
            val isCoordinator = dkgRitualViewModel.isCoordinator()
            val isActionPending = dkgRitualViewModel.isActionPending.value

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = "Shared key for ${dkgRitualUIState.localChatRoom.chatRoom.subject ?: "this group"}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                },
                bottomBar = {
                    // Only the coordinator can open a ritual, and only when there
                    // isn't one already running.
                    if (isCoordinator && (session == null || session.stage == DkgRitualStage.FAILED)) {
                        BottomAppBar(
                            floatingActionButton = {
                                ExtendedFloatingActionButton(
                                    onClick = { dkgRitualViewModel.startRitual() }
                                ) {
                                    if (isActionPending) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                    } else {
                                        Icon(Icons.Default.Key, contentDescription = "Start")
                                    }

                                    Text(
                                        text = if (session == null) "Start key ceremony" else "Try again"
                                    )
                                }
                            },
                            actions = {
                                Text(
                                    modifier = Modifier.padding(start = 15.dp),
                                    text = "${dkgRitualViewModel.threshold.value} of $participantCount",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        )
                    }
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (session == null) {
                        Text(
                            text = "The group can hold one key together, split so that no single member holds it. Signing with it takes a quorum.",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Text(
                            text = "Everyone has to be online at the same time — the ceremony can only finish once all $participantCount of you have taken part.",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        if (isCoordinator) {
                            QuorumStepper(
                                threshold = dkgRitualViewModel.threshold.value,
                                participantCount = participantCount,
                                quorumRange = dkgRitualViewModel.quorumRange(),
                                onThresholdChange = dkgRitualViewModel::setThreshold
                            )
                        } else {
                            Text(
                                text = "Waiting for the group's creator to start it.",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    } else {
                        RitualProgress(
                            session = session,
                            participantCount = participantCount,
                            hostKeyCount = dkgRitualUIState.hostKeyCount,
                            round1Count = dkgRitualUIState.round1Count,
                            round2Count = dkgRitualUIState.round2Count
                        )
                    }
                }
            }
        }
        DkgRitualUIState.Loading -> {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Shared key",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                LoadingDataIndicator(fillScreen = false)
                Spacer(modifier = Modifier.weight(2f))
            }
        }
    }

    LaunchedEffect(true) {
        if (initialDkgRitualUIState == DkgRitualUIState.Loading) {
            dkgRitualViewModel.initiate()
        }
    }
}

/** The ladder. Each round names who it is still waiting on, by count. */
@Composable
private fun RitualProgress(
    session: DkgSession,
    participantCount: Int,
    hostKeyCount: Int,
    round1Count: Int,
    round2Count: Int,
) {
    val stage = session.stage

    if (stage == DkgRitualStage.FAILED) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(15.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("The ceremony was abandoned.", style = MaterialTheme.typography.titleSmall)
                    session.failureReason?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        "Nothing was created and no key exists. It is safe to run it again.",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
        return
    }

    Text(
        text = "${session.threshold} of $participantCount members will be needed to sign with this key.",
        style = MaterialTheme.typography.bodyMedium
    )

    RitualStep(
        title = "Everyone joins",
        detail = "Each member publishes the key they will take part with.",
        count = hostKeyCount,
        total = participantCount,
        isDone = stage != DkgRitualStage.COLLECTING_HOST_KEYS
    )

    RitualStep(
        title = "Round one",
        detail = "Each member commits to their part of the key.",
        count = round1Count,
        total = participantCount,
        isDone = stage == DkgRitualStage.COLLECTING_ROUND_2 || stage == DkgRitualStage.COMPLETE
    )

    RitualStep(
        title = "Round two",
        detail = "Each member confirms everyone else's part.",
        count = round2Count,
        total = participantCount,
        isDone = stage == DkgRitualStage.COMPLETE
    )

    if (stage == DkgRitualStage.COMPLETE) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(15.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Text("The group has a shared key.", style = MaterialTheme.typography.titleSmall)
                }

                session.thresholdPublicKey?.let { thresholdPublicKey ->
                    Text(
                        text = "Key: ${thresholdPublicKey.take(16)}…",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Text(
                    text = "Your share of it is on this device only. Your wallet backup restores it — nobody else's share can.",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun RitualStep(
    title: String,
    detail: String,
    count: Int,
    total: Int,
    isDone: Boolean,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null
                )
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "$count of $total",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            LinearProgressIndicator(
                progress = { if (total == 0) 0f else count.toFloat() / total.toFloat() },
                modifier = Modifier.fillMaxWidth()
            )

            Text(text = detail, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Same shape as the group-creation quorum picker, so the choice reads the same. */
@Composable
private fun QuorumStepper(
    threshold: Int,
    participantCount: Int,
    quorumRange: IntRange,
    onThresholdChange: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = "How many members will it take to sign?",
            style = MaterialTheme.typography.titleSmall
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledIconButton(
                onClick = { onThresholdChange(threshold - 1) },
                enabled = threshold > quorumRange.first
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Fewer signers")
            }

            Text(
                text = "$threshold of $participantCount",
                style = MaterialTheme.typography.titleMedium
            )

            FilledIconButton(
                onClick = { onThresholdChange(threshold + 1) },
                enabled = threshold < quorumRange.last
            ) {
                Icon(Icons.Default.Add, contentDescription = "More signers")
            }
        }

        Text(
            text = "This is fixed once the ceremony runs. Changing it later means generating a new key.",
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Preview
@Composable
private fun DkgRitualScreenPreview() {
    TorchTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            DkgRitualScreen(
                activeUserPublicKey = "",
                chatRoomId = "chatRoomId",
                initialDkgRitualUIState = DkgRitualUIState.Loaded(
                    localChatRoom = LocalChatRoom(
                        chatRoom = ChatRoom(
                            id = "",
                            userPublicKey = "",
                            subject = "Group Discussions",
                            description = "See something... say something.",
                            initialGiftWrapPayloadId = null,
                            mlsGroupState = null
                        ),
                    )
                ),
                activeWalletStateFlow = MutableStateFlow(null),
                chatRepository = ChatRepository.NO_OP_CHAT_REPOSITORY,
                dkgRepository = DkgRepository.NO_OP_DKG_REPOSITORY
            )
        }
    }
}
