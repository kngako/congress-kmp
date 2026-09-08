package press.mantra.compose.ui.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Schema
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Unsubscribe
import androidx.compose.material.icons.filled.WaterfallChart
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import press.mantra.compose.database.model.ChatRoom
import press.mantra.compose.database.model.Participant
import press.mantra.compose.database.model.Profile
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.database.model.intermdiate.LocalParticipant
import press.mantra.compose.repository.ChatRepository
import press.mantra.compose.repository.MantraRepository
import press.mantra.compose.repository.NostrRepository
import press.mantra.compose.ui.composable.navigation.routes.ImplementationPendingRoute
import press.mantra.compose.ui.composable.navigation.routes.Route
import press.mantra.compose.ui.composable.navigation.routes.DkgRitualRoute
import press.mantra.compose.ui.composable.navigation.routes.ProposalListRoute
import press.mantra.compose.ui.composable.navigation.routes.SearchMemberToAddToChatRoomRoute
import press.mantra.compose.ui.composable.widgets.LoadingDataIndicator
import press.mantra.compose.ui.composable.widgets.profile.ProfileAvatar
import press.mantra.compose.ui.theme.TorchTheme
import press.mantra.compose.ui.view.model.ChatRoomDetailViewModel
import press.mantra.compose.ui.view.state.ChatRoomDetailUIState
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import press.mantra.compose.ui.composable.navigation.routes.AddArtifactRoute
import press.mantra.compose.ui.composable.navigation.routes.AddDialectRoute
import press.mantra.compose.ui.composable.navigation.routes.ArtifactDetailRoute
import press.mantra.compose.ui.theme.spacing
import mantra.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import mantra.composeapp.generated.resources.add_artifact_to_library
import mantra.composeapp.generated.resources.add_dialect
import mantra.composeapp.generated.resources.create_project
import mantra.composeapp.generated.resources.delete_group
import mantra.composeapp.generated.resources.dialects
import mantra.composeapp.generated.resources.direct_message_detail
import mantra.composeapp.generated.resources.direct_message_functionality_will_be_here
import mantra.composeapp.generated.resources.invite_new_member
import mantra.composeapp.generated.resources.leave_group
import mantra.composeapp.generated.resources.library
import mantra.composeapp.generated.resources.members_2
import mantra.composeapp.generated.resources.no_artifacts_exists_in_this_groups_library
import mantra.composeapp.generated.resources.no_dialects_have_been_defined_in_this_group_2
import mantra.composeapp.generated.resources.no_projects_exists_in_this_group
import mantra.composeapp.generated.resources.projects
import mantra.composeapp.generated.resources.proposals
import mantra.composeapp.generated.resources.reindex_events
import mantra.composeapp.generated.resources.shared_key
import mantra.composeapp.generated.resources.event_s_still_unreadable
import mantra.composeapp.generated.resources.recovered_of_event_s
import mantra.composeapp.generated.resources.recovered_of_still_unreadable
import androidx.compose.material3.SnackbarHost
import press.mantra.compose.ui.composable.widgets.LocalSnackbarHostState
import press.mantra.compose.ui.theme.readableContent
import press.mantra.compose.ui.composable.widgets.ScreenStateTransition
import press.mantra.compose.ui.theme.ConformancePreviews

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChatRoomDetailScreen(
    activeUserPublicKey: HexKey,
    chatRoomId: String,
    relayHint: String?,
    initialChatRoomDetailUIState: ChatRoomDetailUIState = ChatRoomDetailUIState.Loading,
    nostrRepository: NostrRepository,
    chatRepository: ChatRepository,
    mantraRepository: MantraRepository,
    onNavigateToRoute: (Route) -> Unit,
    onPopBackToRoute: (Route) -> Unit
) {
    val chatRoomDetailViewModel: ChatRoomDetailViewModel = viewModel(
        factory = ChatRoomDetailViewModel.factory(
            chatRoomId = chatRoomId,
            relayHint = relayHint,
            initialChatRoomDetailUIState = initialChatRoomDetailUIState,
            nostrRepository = nostrRepository,
            chatRepository = chatRepository,
            mantraRepository = mantraRepository,
            activeUserPublicKey = activeUserPublicKey
        )
    )

    ScreenStateTransition(chatRoomDetailViewModel.chatRoomDetailUIState) { uiState ->
    when (val chatRoomDetailUIState = uiState) {
            is ChatRoomDetailUIState.Error -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(
                        modifier = Modifier.height(MaterialTheme.spacing.space600)
                    )
                    Text(
                        text = chatRoomDetailUIState.message,
                    )
                }
            }
            is ChatRoomDetailUIState.Loaded -> {
                Scaffold(
                    snackbarHost = { SnackbarHost(LocalSnackbarHostState.current) },
                    topBar = {
                        TopAppBar(
                            title = {
                                chatRoomDetailUIState.localChatRoom.RenderChatRoomTitleText()
                            },
                        )
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier.padding(innerPadding).readableContent().fillMaxSize()
                    ) {
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth().padding(MaterialTheme.spacing.space250),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space125)
                        ) {
                            item {
                                chatRoomDetailUIState.localChatRoom.chatRoom.description?.let {
                                    Text(
                                        text = it,
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }

                            item {
                                HorizontalDivider()
                            }

                            item {
                                // Artifacts
                                Text(
                                    text = stringResource(Res.string.library),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }

                            if (chatRoomDetailUIState.artifacts.isEmpty()) {
                                item {
                                    Text(stringResource(Res.string.no_artifacts_exists_in_this_groups_library))
                                }
                            } else {
                                items(
                                    items = chatRoomDetailUIState.artifacts,
                                    key = { artifact -> artifact.id }
                                ) { artifact ->
                                    Card(
                                        onClick = {
                                            onNavigateToRoute.invoke(
                                                ArtifactDetailRoute(
                                                    activeUserPublicKey = activeUserPublicKey,
                                                    artifactId = artifact.id,
                                                    chatRoomId = chatRoomId,
                                                    relayHint = relayHint
                                                )
                                            )
                                        }
                                    ) {
                                        ListItem(
                                            leadingContent = {
                                                Icon(
                                                    Icons.Default.LibraryBooks,
                                                    contentDescription = "Artifact"
                                                )
                                            },
                                            trailingContent = {
                                                Icon(
                                                    Icons.Default.ChevronRight,
                                                    contentDescription = "View artifact"
                                                )
                                            },
                                            headlineContent = {
                                                Text(text = artifact.name)
                                            },
                                            supportingContent = {
                                                Text(text = artifact.url)
                                            }
                                        )
                                    }
                                }
                            }

                            item {
                                TextButton(
                                    onClick = {
                                        // Navigate to the invite user screen...
                                        onNavigateToRoute.invoke(
                                            AddArtifactRoute(
                                                chatRoomId = chatRoomId,
                                                activeUserPublicKey = activeUserPublicKey,
                                                relayHint = relayHint
                                            )
                                        )
                                    }
                                ) {

                                    Icon(
                                        Icons.Default.LibraryBooks,
                                        contentDescription = "Add new artifact"
                                    )

                                    Spacer(
                                        modifier = Modifier.width(MaterialTheme.spacing.space125)
                                    )

                                    Text(stringResource(Res.string.add_artifact_to_library))
                                }
                            }

                            item {
                                HorizontalDivider()
                            }

                            item {
                                // Dialects
                                Text(
                                    text = stringResource(Res.string.dialects),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }

                            if (chatRoomDetailUIState.dialects.isEmpty()) {
                                item {
                                    Text(stringResource(Res.string.no_dialects_have_been_defined_in_this_group_2))
                                }
                            } else {
                                items(
                                    items = chatRoomDetailUIState.dialects,
                                    key = { dialect -> dialect.id }
                                ) { dialect ->
                                    Card {
                                        ListItem(
                                            leadingContent = {
                                                Icon(
                                                    Icons.Default.Translate,
                                                    contentDescription = "Dialect"
                                                )
                                            },
                                            headlineContent = {
                                                Text(text = dialect.name)
                                            },
                                            supportingContent = {
                                                Text(text = "${dialect.language} \u00b7 ${dialect.country}")
                                            }
                                        )
                                    }
                                }
                            }

                            item {
                                TextButton(
                                    onClick = {
                                        onNavigateToRoute.invoke(
                                            AddDialectRoute(
                                                chatRoomId = chatRoomId,
                                                activeUserPublicKey = activeUserPublicKey,
                                                relayHint = relayHint
                                            )
                                        )
                                    }
                                ) {

                                    Icon(
                                        Icons.Default.Translate,
                                        contentDescription = "Add new dialect"
                                    )

                                    Spacer(
                                        modifier = Modifier.width(MaterialTheme.spacing.space125)
                                    )

                                    Text(stringResource(Res.string.add_dialect))
                                }
                            }

                            item {
                                HorizontalDivider()
                            }

                            // TODO: Add projects...

                            item {
                                // Artifacts
                                Text(
                                    text = stringResource(Res.string.projects),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }

                            item {
                                Text(stringResource(Res.string.no_projects_exists_in_this_group))
                            }

                            item {
                                TextButton(
                                    onClick = {
                                        // Navigate to the invite user screen...
                                        onNavigateToRoute.invoke(
                                            ImplementationPendingRoute(
                                                "Add new project"
                                            )
                                        )
                                    }
                                ) {

                                    Icon(
                                        Icons.Default.Schema,
                                        contentDescription = "create new projec"
                                    )

                                    Spacer(
                                        modifier = Modifier.width(MaterialTheme.spacing.space125)
                                    )

                                    Text(stringResource(Res.string.create_project))
                                }
                            }

                            item {
                                HorizontalDivider()
                            }

                            item {
                                Text(
                                    text = stringResource(Res.string.members_2),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }

                            // A shared threshold key only means something where every
                            // member is an equal admin, which is the NIP-17 (robust) case.
                            // MLS rooms have one admin and no group key to share.
                            if (chatRoomDetailUIState.localChatRoom.chatRoom.mlsGroupState == null) {
                                item {
                                    TextButton(
                                        onClick = {
                                            onNavigateToRoute.invoke(
                                                DkgRitualRoute(
                                                    activeUserPublicKey = activeUserPublicKey,
                                                    chatRoomId = chatRoomId
                                                )
                                            )
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.Key,
                                            contentDescription = "Shared key"
                                        )

                                        Spacer(
                                            modifier = Modifier.width(MaterialTheme.spacing.space125)
                                        )

                                        Text(stringResource(Res.string.shared_key))
                                    }
                                }
                            }

                            // Every room, not only the one that holds the key. A
                            // ceremony needs a NIP-17 group, but a signing message is
                            // a marmot inner event -- see `FrostSigningManager.broadcast`
                            // -- so the room a group actually proposes in is the MLS
                            // one, which is the last place this should be missing from.
                            //
                            // The transcript carries a proposal past as it happens;
                            // this is where a member goes to find one that has scrolled
                            // away, or to see what the group has signed.
                            item {
                                TextButton(
                                    onClick = {
                                        onNavigateToRoute.invoke(
                                            ProposalListRoute(
                                                activeUserPublicKey = activeUserPublicKey,
                                                chatRoomId = chatRoomId
                                            )
                                        )
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Draw,
                                        contentDescription = "Proposals"
                                    )

                                    Spacer(
                                        modifier = Modifier.width(MaterialTheme.spacing.space125)
                                    )

                                    Text(stringResource(Res.string.proposals))
                                }
                            }

                            item {
                                TextButton(
                                    onClick = {
                                        // Navigate to the invite user screen...
                                        onNavigateToRoute.invoke(
                                            SearchMemberToAddToChatRoomRoute(
                                                chatRoomId = chatRoomId,
                                                activeUserPublicKey = activeUserPublicKey,
                                                relayHint = relayHint
                                            )
                                        )
                                    }
                                ) {

                                    Icon(
                                        Icons.Default.PersonAdd,
                                        contentDescription = "Invite new member"
                                    )

                                    Spacer(
                                        modifier = Modifier.width(MaterialTheme.spacing.space125)
                                    )

                                    Text(stringResource(Res.string.invite_new_member))
                                }
                            }

                            items(
                                items = chatRoomDetailUIState.localChatRoom.localParticipants,
                                key = { localParticipant -> localParticipant.participant.participantPublicKey}
                            ) { localParticipant ->

                                Card(
                                    onClick = {
                                        onNavigateToRoute.invoke(
                                            ImplementationPendingRoute(
                                                "Profile detail"
                                            )
                                        )
                                    }
                                ) {
                                    ListItem(
                                        leadingContent = {
                                            ProfileAvatar(
                                                publicKey = localParticipant.participant.participantPublicKey,
                                                profile = localParticipant.profile
                                            )
                                        },
                                        trailingContent = {
                                            Icon(
                                                Icons.Default.ChevronRight,
                                                contentDescription = "View profile"
                                            )
                                        },
                                        headlineContent = {
                                            Text(
                                                text = localParticipant.profile?.humanReadableNameOrPubkey() ?: localParticipant.participant.participantPublicKey
                                            )
                                        },
                                        supportingContent = {
                                            localParticipant.profile?.about?.let {
                                                Text(
                                                    text = it
                                                )
                                            }
                                        }

                                    )
                                }

                            }


                            item {
                                HorizontalDivider()
                            }

                            item {

                                TextButton(
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.secondary
                                    ),
                                    enabled = chatRoomDetailUIState.localChatRoom.chatRoom.leftGroupAt == null, // Disable after we leave group...
                                    onClick = {
                                        chatRoomDetailViewModel.leaveGroup(
                                            localChatRoom = chatRoomDetailUIState.localChatRoom,
                                            onPopBackToRoute = onPopBackToRoute
                                        )
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Unsubscribe,
                                        contentDescription = "Leave group"
                                    )

                                    Spacer(
                                        modifier = Modifier.width(MaterialTheme.spacing.space125)
                                    )

                                    Text(stringResource(Res.string.leave_group))
                                }

                                // What it actually does: sets leftGroupAt and posts a line to
                                // the room. Said here because the button fires immediately --
                                // there is no confirmation step to say it in.
                                Text(
                                    text = "Posts a line to the room saying you left, and " +
                                        "lets you delete it from this device afterwards.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(
                                        start = MaterialTheme.spacing.space600,
                                        end = MaterialTheme.spacing.containerPadding,
                                        bottom = MaterialTheme.spacing.compactPadding,
                                    )
                                )
                            }

                            item {
                                TextButton(
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    enabled = chatRoomDetailUIState.localChatRoom.chatRoom.leftGroupAt != null,
                                    onClick = {
                                        chatRoomDetailViewModel.softDeleteGroup(
                                            localChatRoom = chatRoomDetailUIState.localChatRoom,
                                            onPopBackToRoute = onPopBackToRoute
                                        )
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.DeleteForever,
                                        contentDescription = "Delete group"
                                    )

                                    Spacer(
                                        modifier = Modifier.width(MaterialTheme.spacing.space125)
                                    )

                                    Text(stringResource(Res.string.delete_group))
                                }

                                // A soft delete: it sets deletedAt on the local row. Saying
                                // "delete" without saying where would leave somebody believing
                                // the messages are gone from the relays and from the other
                                // members, which is the opposite of true.
                                Text(
                                    text = "Removes the room from this device. The messages " +
                                        "stay on the relays and with the other members.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(
                                        start = MaterialTheme.spacing.space600,
                                        end = MaterialTheme.spacing.containerPadding,
                                        bottom = MaterialTheme.spacing.compactPadding,
                                    )
                                )
                            }

                            // Only MLS rooms have group events to read again. A NIP-17
                            // room's messages are gift wraps, which carry no ordering
                            // for anything to go wrong with.
                            if (chatRoomDetailUIState.localChatRoom.chatRoom.mlsGroupState != null) {
                                item {
                                    HorizontalDivider()
                                }

                                item {
                                    ReindexMarmotGroupEventsButton(
                                        reindexState = chatRoomDetailViewModel.reindexState,
                                        onReindex = chatRoomDetailViewModel::reindexMarmotGroupEvents
                                    )
                                }
                            }
                        }
                    }
                }
            }
            ChatRoomDetailUIState.Loading -> {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(
                        MaterialTheme.spacing.space250
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space250)
                ) {

                    Spacer(
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = stringResource(Res.string.direct_message_detail),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = stringResource(Res.string.direct_message_functionality_will_be_here),
                        style = MaterialTheme.typography.bodySmall,
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
    }

    LaunchedEffect(true) {
        if (initialChatRoomDetailUIState == ChatRoomDetailUIState.Loading) {
            chatRoomDetailViewModel.initiateChatRoomDetail()
        }
    }
}

/**
 * Reads the room's stored marmot group events again.
 *
 * Sits at the very bottom, below leaving and deleting, because it is a repair
 * rather than something anyone should need in the ordinary course of using the
 * group. It says what it found afterwards -- a replay that recovered nothing
 * looks identical to one that was never pressed, and the difference between
 * "nothing was broken" and "nothing could be fixed" is worth telling.
 */
@Composable
private fun ReindexMarmotGroupEventsButton(
    reindexState: ChatRoomDetailViewModel.ReindexState,
    onReindex: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space50)
    ) {
        TextButton(
            enabled = reindexState !is ChatRoomDetailViewModel.ReindexState.Running,
            onClick = onReindex
        ) {
            if (reindexState is ChatRoomDetailViewModel.ReindexState.Running) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    Icons.Default.Autorenew,
                    contentDescription = "Reindex group events"
                )
            }

            Spacer(
                modifier = Modifier.width(MaterialTheme.spacing.space125)
            )

            Text(stringResource(Res.string.reindex_events))
        }

        when (reindexState) {
            is ChatRoomDetailViewModel.ReindexState.Done -> {
                val report = reindexState.report
                // What was published before this member joined is named rather
                // than counted as read. Their epoch keys were never on this
                // device, so "all read" would be a claim about messages nobody
                // here can open -- and a room that is mostly older than the
                // member is the ordinary case for anyone invited into one.
                val beforeJoining =
                    if (report.predatingMembership > 0) {
                        " · ${report.predatingMembership} from before you joined"
                    } else {
                        ""
                    }
                Text(
                    text = when {
                        report.isNoOp ->
                            "Nothing to reindex · ${report.stored - report.predatingMembership} " +
                                "event(s) all read$beforeJoining"
                        report.recovered > 0 && report.failed > 0 ->
                            stringResource(Res.string.recovered_of_still_unreadable, report.recovered, report.unresolved, report.failed, beforeJoining)
                        report.recovered > 0 ->
                            stringResource(Res.string.recovered_of_event_s, report.recovered, report.unresolved, beforeJoining)
                        else -> stringResource(Res.string.event_s_still_unreadable, report.failed, beforeJoining)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }

            is ChatRoomDetailViewModel.ReindexState.Failed -> {
                Text(
                    text = reindexState.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }

            else -> Unit
        }
    }
}

@ConformancePreviews
@Composable
private fun ChatRoomMessagingScreenPreview() {
    TorchTheme {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            ChatRoomDetailScreen(
                activeUserPublicKey = "",
                chatRoomId = "publicKey",
                relayHint = null,
                initialChatRoomDetailUIState = ChatRoomDetailUIState.Loaded(
                    localChatRoom = LocalChatRoom(
                        chatRoom = ChatRoom(
                            id = "",
                            userPublicKey = "",
                            subject = "Message title",
                            description = "Description of a chat room so that members now why they are here.",
                            initialGiftWrapPayloadId = "sdfaer",
                            mlsGroupState = null
                        ),
                        localParticipants = listOf(
                            LocalParticipant(
                                participant = Participant(
                                    id = 2,
                                    participantPublicKey = "gex",
                                    chatRoomId = "chatRoomId",
                                    relayHint = null
                                ),
                                profile = Profile(
                                    publicKey = "gex",
                                    displayName = "Gexie",
                                    nostrEventId = "nosrtEventId"
                                )
                            )
                        )
                    )
                ),
                nostrRepository = NostrRepository.NO_OP_NOSTR_REPOSITORY,
                chatRepository = ChatRepository.NO_OP_CHAT_REPOSITORY,
                mantraRepository = MantraRepository.NO_OP_MANTRA_REPOSITORY,
                onNavigateToRoute = {},
                onPopBackToRoute = {}
            )
        }
    }
}