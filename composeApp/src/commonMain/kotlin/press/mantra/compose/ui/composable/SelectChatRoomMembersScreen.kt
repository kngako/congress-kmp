package press.mantra.compose.ui.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
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
import press.mantra.compose.database.model.Profile
import press.mantra.compose.repository.ChatRepository
import press.mantra.compose.repository.NostrRepository
import press.mantra.compose.ui.composable.navigation.routes.Route
import press.mantra.compose.ui.composable.widgets.LoadingDataIndicator
import press.mantra.compose.ui.composable.widgets.profile.ProfileAvatar
import press.mantra.compose.ui.theme.TorchTheme
import press.mantra.compose.ui.view.model.SelectChatRoomMembersViewModel
import press.mantra.compose.ui.view.state.SelectChatRoomMembersUIState
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import fr.acinq.phoenix.data.ActiveWallet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Second step of group creation: pick who is in the group, then create it.
 *
 * The list is whatever profiles are already in the local store — no directory
 * lookup happens here.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SelectChatRoomMembersScreen(
    activeUserPublicKey: HexKey,
    name: String,
    description: String?,
    initialSelectChatRoomMembersUIState: SelectChatRoomMembersUIState = SelectChatRoomMembersUIState.Loading,
    activeWalletStateFlow: StateFlow<ActiveWallet?>,
    nostrRepository: NostrRepository,
    chatRepository: ChatRepository,
    onNavigateToRoute: (Route) -> Unit,
) {
    val selectChatRoomMembersViewModel: SelectChatRoomMembersViewModel = viewModel(
        factory = SelectChatRoomMembersViewModel.factory(
            activeUserPublicKey = activeUserPublicKey,
            name = name,
            description = description,
            initialSelectChatRoomMembersUIState = initialSelectChatRoomMembersUIState,
            activeWalletStateFlow = activeWalletStateFlow,
            nostrRepository = nostrRepository,
            chatRepository = chatRepository
        )
    )

    when (val selectChatRoomMembersUIState = selectChatRoomMembersViewModel.selectChatRoomMembersUIState) {
        is SelectChatRoomMembersUIState.Error -> {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(
                    modifier = Modifier.height(50.dp)
                )
                Text(
                    text = selectChatRoomMembersUIState.message,
                    textAlign = TextAlign.Center
                )
            }
        }
        is SelectChatRoomMembersUIState.Loaded -> {
            val isActionPending = selectChatRoomMembersViewModel.isActionPending.value
            val selectedCount = selectChatRoomMembersViewModel.selectedPublicKeys.size
            val membersNotAdded = selectChatRoomMembersViewModel.membersNotAdded

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = "Add people to $name",
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
                                    selectChatRoomMembersViewModel.createChatRoom(
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
                                    text = when {
                                        selectChatRoomMembersViewModel.createdChatRoomId.value != null -> "Open chat"
                                        selectedCount > 0 -> "Create chat with $selectedCount"
                                        else -> "Create chat"
                                    }
                                )
                            }
                        },
                        actions = {
                            Text(
                                modifier = Modifier.padding(start = 15.dp),
                                text = if (selectedCount > 0) {
                                    "$selectedCount selected"
                                } else {
                                    "No one selected yet"
                                },
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    )
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier.padding(innerPadding).fillMaxSize()
                ) {
                    if (membersNotAdded.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Text(
                                modifier = Modifier.padding(15.dp),
                                text = "${name} was created, but ${membersNotAdded.joinToString { it.humanReadableNameOrPubkey() }} couldn't be added yet. Invite them again from the chat once they're on Torch.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    if (selectChatRoomMembersUIState.profiles.isEmpty()) {
                        Column(
                            modifier = Modifier.weight(1f).fillMaxWidth().padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Spacer(
                                modifier = Modifier.weight(1f)
                            )

                            Text(
                                text = "No one to add yet.",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = "Search for people and chat with them first — everyone you know locally shows up here.",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = "You can still create the chat now and invite people later.",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )

                            Spacer(
                                modifier = Modifier.weight(2f)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth().padding(5.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(
                                items = selectChatRoomMembersUIState.profiles,
                                key = { it.publicKey }
                            ) { profile ->
                                val isSelected = selectChatRoomMembersViewModel.isSelected(profile.publicKey)

                                Card(
                                    onClick = {
                                        selectChatRoomMembersViewModel.toggleMember(profile.publicKey)
                                    }
                                ) {
                                    ListItem(
                                        leadingContent = {
                                            ProfileAvatar(
                                                profile = profile
                                            )
                                        },
                                        headlineContent = {
                                            Text(
                                                profile.humanReadableNameOrPubkey()
                                            )
                                        },
                                        supportingContent = {
                                            profile.about?.let {
                                                Text(
                                                    text = it,
                                                    overflow = TextOverflow.Ellipsis,
                                                    maxLines = 1
                                                )
                                            }
                                        },
                                        trailingContent = {
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = {
                                                    selectChatRoomMembersViewModel.toggleMember(profile.publicKey)
                                                }
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        SelectChatRoomMembersUIState.Loading -> {
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
                    text = "Add people to $name",
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
        if (initialSelectChatRoomMembersUIState == SelectChatRoomMembersUIState.Loading) {
            selectChatRoomMembersViewModel.initiate()
        }
    }
}

@Preview
@Composable
private fun SelectChatRoomMembersScreenPreview() {
    TorchTheme {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            SelectChatRoomMembersScreen(
                activeUserPublicKey = "",
                name = "Group Discussions",
                description = "See something... say something.",
                initialSelectChatRoomMembersUIState = SelectChatRoomMembersUIState.Loaded(
                    profiles = listOf(
                        Profile(
                            publicKey = "hex",
                            displayName = "Man With A Plan",
                            about = "Making plans and getting things done...",
                            nostrEventId = "nostrEventId"
                        )
                    )
                ),
                activeWalletStateFlow = MutableStateFlow(null),
                nostrRepository = NostrRepository.NO_OP_NOSTR_REPOSITORY,
                chatRepository = ChatRepository.NO_OP_CHAT_REPOSITORY,
                onNavigateToRoute = {}
            )
        }
    }
}
