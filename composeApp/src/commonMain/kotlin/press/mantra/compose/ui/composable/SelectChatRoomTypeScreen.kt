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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Groups
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import press.mantra.compose.database.model.Profile
import press.mantra.compose.database.model.types.ChatRoomType
import press.mantra.compose.repository.ChatRepository
import press.mantra.compose.repository.DkgRepository
import press.mantra.compose.repository.NostrRepository
import press.mantra.compose.ui.composable.navigation.routes.Route
import press.mantra.compose.ui.composable.widgets.LoadingDataIndicator
import press.mantra.compose.ui.theme.TorchTheme
import press.mantra.compose.ui.view.model.SelectChatRoomTypeViewModel
import press.mantra.compose.ui.view.state.SelectChatRoomTypeUIState
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import fr.acinq.phoenix.data.ActiveWallet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import press.mantra.compose.ui.theme.spacing

/**
 * Last step of group creation: convenient (one admin) or robust (everyone
 * admins, changes need a threshold to approve), and then build the group.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SelectChatRoomTypeScreen(
    activeUserPublicKey: HexKey,
    name: String,
    description: String?,
    memberPublicKeys: List<HexKey>,
    initialSelectChatRoomTypeUIState: SelectChatRoomTypeUIState = SelectChatRoomTypeUIState.Loading,
    activeWalletStateFlow: StateFlow<ActiveWallet?>,
    nostrRepository: NostrRepository,
    chatRepository: ChatRepository,
    dkgRepository: DkgRepository,
    onNavigateToRoute: (Route) -> Unit,
) {
    val selectChatRoomTypeViewModel: SelectChatRoomTypeViewModel = viewModel(
        factory = SelectChatRoomTypeViewModel.factory(
            activeUserPublicKey = activeUserPublicKey,
            name = name,
            description = description,
            memberPublicKeys = memberPublicKeys,
            initialSelectChatRoomTypeUIState = initialSelectChatRoomTypeUIState,
            activeWalletStateFlow = activeWalletStateFlow,
            nostrRepository = nostrRepository,
            chatRepository = chatRepository,
            dkgRepository = dkgRepository
        )
    )

    when (val selectChatRoomTypeUIState = selectChatRoomTypeViewModel.selectChatRoomTypeUIState) {
        is SelectChatRoomTypeUIState.Error -> {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(
                    modifier = Modifier.height(MaterialTheme.spacing.space600)
                )
                Text(
                    text = selectChatRoomTypeUIState.message,
                    textAlign = TextAlign.Center
                )
            }
        }
        is SelectChatRoomTypeUIState.Loaded -> {
            val isActionPending = selectChatRoomTypeViewModel.isActionPending.value
            val selectedChatRoomType = selectChatRoomTypeViewModel.selectedChatRoomType.value
            val membersNotAdded = selectChatRoomTypeViewModel.membersNotAdded
            val keyCeremonyNotStarted = selectChatRoomTypeViewModel.keyCeremonyNotStarted.value
            val adminCount = selectChatRoomTypeViewModel.adminCount
            val isRobustAvailable = selectChatRoomTypeViewModel.isRobustAvailable
            val isRobustSelected = selectedChatRoomType == ChatRoomType.ROBUST

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = "How should $name be run?",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                },
                bottomBar = {
                    BottomAppBar(
                        floatingActionButton = {
                            ExtendedFloatingActionButton(
                                onClick = {
                                    selectChatRoomTypeViewModel.createChatRoom(
                                        onNavigateToRoute = onNavigateToRoute
                                    )
                                }
                            ) {
                                if (isActionPending) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Create chat"
                                    )
                                }

                                Text(
                                    text = if (selectChatRoomTypeViewModel.createdChatRoomId.value != null) {
                                        "Open chat"
                                    } else {
                                        "Create chat"
                                    }
                                )
                            }
                        },
                        actions = {
                            Text(
                                modifier = Modifier.padding(start = MaterialTheme.spacing.space200),
                                text = when (selectChatRoomTypeUIState.members.size) {
                                    0 -> "Just you for now"
                                    1 -> "You and 1 other"
                                    else -> "You and ${selectChatRoomTypeUIState.members.size} others"
                                },
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    )
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
                    if (membersNotAdded.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Text(
                                modifier = Modifier.padding(MaterialTheme.spacing.space200),
                                text = "$name was created, but ${membersNotAdded.joinToString { selectChatRoomTypeViewModel.displayNameFor(it) }} couldn't be added yet. Invite them again from the chat once they're on Torch.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    if (keyCeremonyNotStarted) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Text(
                                modifier = Modifier.padding(MaterialTheme.spacing.space200),
                                text = "$name was created, but its shared key ceremony couldn't be started. Open the chat and start it from the group's details — until then the group has no key of its own.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    Text(
                        text = "This decides who can change the group later. You can't switch afterwards.",
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)
                    )

                    ChatRoomTypeCard(
                        icon = Icons.Default.Bolt,
                        title = "Convenient",
                        summary = "You are the only admin.",
                        detail = "You can add or remove people and change the group's name or description on your own, without waiting for anybody.",
                        footnote = "Nothing about the group can change unless you do it — and nobody else can carry it on if you lose your keys.",
                        isSelected = selectedChatRoomType == ChatRoomType.CONVENIENT,
                        onClick = {
                            selectChatRoomTypeViewModel.selectChatRoomType(ChatRoomType.CONVENIENT)
                        }
                    )

                    ChatRoomTypeCard(
                        icon = Icons.Default.Groups,
                        title = "Robust",
                        summary = "Every member is an admin.",
                        detail = if (isRobustAvailable) {
                            "All $adminCount of you administer the group together. Any change — adding or removing someone, renaming the group — has to be approved by a quorum of you before it takes effect."
                        } else {
                            "Everyone administers the group together. Any change — adding or removing someone, renaming the group — has to be approved by a quorum of the admins before it takes effect."
                        },
                        // Says what tapping create actually does, because it is the one
                        // thing here that asks something of everybody else: the group's
                        // first act is a ceremony each member has to approve their way
                        // through before there is a key.
                        footnote = "No single admin can change the group alone, and the group outlives any one of you. Creating the group starts a key ceremony every member takes part in.",
                        isSelected = isRobustSelected,
                        isEnabled = isRobustAvailable,
                        disabledReason = selectChatRoomTypeViewModel.robustUnavailableReason,
                        onClick = {
                            selectChatRoomTypeViewModel.selectChatRoomType(ChatRoomType.ROBUST)
                        }
                    ) {
                        // Ask for the quorum only once robust is the choice — before that
                        // there is no decision to make.
                        if (isRobustSelected) {
                            QuorumPicker(
                                quorum = selectChatRoomTypeViewModel.quorum.value,
                                adminCount = adminCount,
                                quorumRange = selectChatRoomTypeViewModel.quorumRange,
                                explanation = selectChatRoomTypeViewModel.quorumExplanation(),
                                onQuorumChange = selectChatRoomTypeViewModel::setQuorum
                            )
                        }
                    }
                }
            }
        }
        SelectChatRoomTypeUIState.Loading -> {
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
                    text = "How should $name be run?",
                    style = MaterialTheme.typography.bodyLarge,
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
        if (initialSelectChatRoomTypeUIState == SelectChatRoomTypeUIState.Loading) {
            selectChatRoomTypeViewModel.initiate()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatRoomTypeCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    summary: String,
    detail: String,
    footnote: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    isEnabled: Boolean = true,
    disabledReason: String? = null,
    content: @Composable () -> Unit = {},
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        enabled = isEnabled,
        colors = if (isSelected) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.space200),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick = onClick,
                    enabled = isEnabled
                )

                Icon(
                    imageVector = icon,
                    contentDescription = null
                )

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Text(
                text = summary,
                style = MaterialTheme.typography.titleSmall
            )

            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = footnote,
                style = MaterialTheme.typography.labelMedium
            )

            disabledReason?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }

            content()
        }
    }
}

/**
 * Asks how many admins have to sign off on a change. Stepped rather than typed:
 * the range is small, both ends are bounded, and the caption has to keep up.
 */
@Composable
private fun QuorumPicker(
    quorum: Int,
    adminCount: Int,
    quorumRange: IntRange,
    explanation: String,
    onQuorumChange: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = MaterialTheme.spacing.space50),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space50)
    ) {
        Text(
            text = "How many admins have to approve a change?",
            style = MaterialTheme.typography.titleSmall
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space200),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledIconButton(
                onClick = { onQuorumChange(quorum - 1) },
                enabled = quorum > quorumRange.first
            ) {
                Icon(
                    Icons.Default.Remove,
                    contentDescription = "Fewer approvals"
                )
            }

            Text(
                text = "$quorum of $adminCount",
                style = MaterialTheme.typography.titleMedium
            )

            FilledIconButton(
                onClick = { onQuorumChange(quorum + 1) },
                enabled = quorum < quorumRange.last
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "More approvals"
                )
            }
        }

        Text(
            text = explanation,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Preview
@Composable
private fun SelectChatRoomTypeScreenPreview() {
    TorchTheme {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            SelectChatRoomTypeScreen(
                activeUserPublicKey = "",
                name = "Group Discussions",
                description = "See something... say something.",
                memberPublicKeys = listOf("hex", "otherHex"),
                initialSelectChatRoomTypeUIState = SelectChatRoomTypeUIState.Loaded(
                    members = listOf(
                        Profile(
                            publicKey = "hex",
                            displayName = "Man With A Plan",
                            about = "Making plans and getting things done...",
                            nostrEventId = "nostrEventId"
                        ),
                        Profile(
                            publicKey = "otherHex",
                            displayName = "Woman Of Few Words",
                            about = "Says it once, says it well.",
                            nostrEventId = "otherNostrEventId"
                        )
                    )
                ),
                activeWalletStateFlow = MutableStateFlow(null),
                nostrRepository = NostrRepository.NO_OP_NOSTR_REPOSITORY,
                chatRepository = ChatRepository.NO_OP_CHAT_REPOSITORY,
                dkgRepository = DkgRepository.NO_OP_DKG_REPOSITORY,
                onNavigateToRoute = {}
            )
        }
    }
}
