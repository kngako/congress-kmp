package press.mantra.compose.ui.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import mantra.composeapp.generated.resources.Res
import mantra.composeapp.generated.resources.a_subgroup_named_s
import mantra.composeapp.generated.resources.a_subgroups_key
import mantra.composeapp.generated.resources.abandoned_no_key_was_created
import mantra.composeapp.generated.resources.check_the_combined_result
import mantra.composeapp.generated.resources.everything_else
import mantra.composeapp.generated.resources.join_the_ceremony_by_publishing_your_key
import mantra.composeapp.generated.resources.key_ceremonies
import mantra.composeapp.generated.resources.open
import mantra.composeapp.generated.resources.review
import mantra.composeapp.generated.resources.s_of_s_members_will_sign_with_it
import mantra.composeapp.generated.resources.send_your_contribution_to_the_key
import mantra.composeapp.generated.resources.set_up_a_shared_key
import mantra.composeapp.generated.resources.the_group_has_a_key_from_this_ceremony
import mantra.composeapp.generated.resources.the_group_has_its_key_finish_setting_it_up
import mantra.composeapp.generated.resources.the_groups_shared_key
import mantra.composeapp.generated.resources.this_group_has_held_no_key_ceremony
import mantra.composeapp.generated.resources.waiting_for_everyone_to_commit_their_part
import mantra.composeapp.generated.resources.waiting_for_everyone_to_confirm_the_result
import mantra.composeapp.generated.resources.waiting_for_members_to_join
import mantra.composeapp.generated.resources.waiting_for_you
import org.jetbrains.compose.resources.stringResource
import press.mantra.compose.database.model.ChatRoom
import press.mantra.compose.database.model.DkgSession
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.database.model.types.DkgApprovalStep
import press.mantra.compose.database.model.types.DkgRitualStage
import press.mantra.compose.extensions.toFormattedTimeAndDateString
import press.mantra.compose.repository.ChatRepository
import press.mantra.compose.repository.DkgRepository
import press.mantra.compose.ui.composable.navigation.routes.DkgRitualRoute
import press.mantra.compose.ui.composable.navigation.routes.Route
import press.mantra.compose.ui.composable.widgets.Decorative
import press.mantra.compose.ui.composable.widgets.LocalSnackbarHostState
import press.mantra.compose.ui.composable.widgets.NavigateBackButton
import press.mantra.compose.ui.theme.ConformancePreviews
import press.mantra.compose.ui.theme.MantraTheme
import press.mantra.compose.ui.theme.readableContent
import press.mantra.compose.ui.theme.spacing
import press.mantra.compose.ui.view.model.DkgRitualListViewModel
import press.mantra.compose.ui.view.state.DkgRitualListUIState

/**
 * Every key ceremony a room holds.
 *
 * The transcript shows a ceremony as it happens, in among everything else that
 * was said, and the standing notice above the composer says when one is waiting
 * on the reader. Neither can say *which*, once there are two -- and since a
 * subgroup's ChillDKG moved into its parent's room there are routinely two: the
 * group's own, long finished, and whatever subgroup is being made this week.
 *
 * So: one row per ceremony, each carrying its own state, the finished ones kept
 * rather than dropped. The ones still waiting on the reader come first, because
 * those are the only ones with anything to do in them; the rest are the record
 * of how this group came to hold the keys it holds, which is the other half of
 * what this screen is for.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DkgRitualListScreen(
    activeUserPublicKey: HexKey,
    chatRoomId: String,
    initialDkgRitualListUIState: DkgRitualListUIState = DkgRitualListUIState.Loading,
    chatRepository: ChatRepository,
    dkgRepository: DkgRepository,
    onNavigateBack: () -> Unit,
    onNavigateToRoute: (Route) -> Unit,
) {
    val dkgRitualListViewModel: DkgRitualListViewModel = viewModel(
        factory = DkgRitualListViewModel.factory(
            chatRoomId = chatRoomId,
            activeUserPublicKey = activeUserPublicKey,
            initialDkgRitualListUIState = initialDkgRitualListUIState,
            chatRepository = chatRepository,
            dkgRepository = dkgRepository
        )
    )

    LaunchedEffect(true) {
        if (initialDkgRitualListUIState == DkgRitualListUIState.Loading) {
            dkgRitualListViewModel.initiate()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(LocalSnackbarHostState.current) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.key_ceremonies),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = { NavigateBackButton(onNavigateBack) }
            )
        }
    ) { padding ->
        when (val state = dkgRitualListViewModel.dkgRitualListUIState.collectAsState().value) {
            is DkgRitualListUIState.Loading -> Column(
                modifier = Modifier.fillMaxWidth().padding(padding).readableContent()
                    .padding(MaterialTheme.spacing.space250),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(MaterialTheme.spacing.space600))
                CircularProgressIndicator()
            }

            is DkgRitualListUIState.Error -> Column(
                modifier = Modifier.fillMaxWidth().padding(padding).readableContent()
                    .padding(MaterialTheme.spacing.space250),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(MaterialTheme.spacing.space600))
                Text(text = state.message, textAlign = TextAlign.Center)
            }

            is DkgRitualListUIState.Loaded -> {
                val open: (DkgRitualListUIState.Ceremony) -> Unit = { ceremony ->
                    onNavigateToRoute.invoke(
                        DkgRitualRoute(
                            activeUserPublicKey = activeUserPublicKey,
                            chatRoomId = chatRoomId,
                            dkgSessionId = ceremony.session.id
                        )
                    )
                }

                LazyColumn(
                    modifier = Modifier.padding(padding).readableContent().fillMaxSize()
                        .padding(MaterialTheme.spacing.space250),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space125)
                ) {
                    if (state.ceremonies.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                                    .padding(top = MaterialTheme.spacing.space600),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(
                                    MaterialTheme.spacing.sectionGap
                                )
                            ) {
                                Text(
                                    text = stringResource(
                                        Res.string.this_group_has_held_no_key_ceremony
                                    ),
                                    textAlign = TextAlign.Center
                                )

                                // The way out of an empty list, rather than a
                                // dead end that sends a member back to the
                                // group's details to find the other door. The
                                // ceremony screen decides whether the group is
                                // in a position to hold one; this only offers
                                // to ask it.
                                FilledTonalButton(onClick = {
                                    onNavigateToRoute.invoke(
                                        DkgRitualRoute(
                                            activeUserPublicKey = activeUserPublicKey,
                                            chatRoomId = chatRoomId
                                        )
                                    )
                                }) {
                                    Icon(Icons.Default.Key, contentDescription = Decorative)

                                    Spacer(
                                        modifier = Modifier.width(
                                            MaterialTheme.spacing.relatedGap
                                        )
                                    )

                                    Text(stringResource(Res.string.set_up_a_shared_key))
                                }
                            }
                        }
                    }

                    if (state.waitingForYou.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(Res.string.waiting_for_you),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        items(
                            items = state.waitingForYou,
                            key = { it.session.id }
                        ) { ceremony ->
                            CeremonyCard(ceremony = ceremony, onClick = { open(ceremony) })
                        }
                    }

                    if (state.rest.isNotEmpty()) {
                        // Headed only when there is a section above to tell it
                        // apart from; on its own it is the whole screen, which
                        // the title already says.
                        if (state.waitingForYou.isNotEmpty()) {
                            item {
                                Text(
                                    text = stringResource(Res.string.everything_else),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }

                        items(
                            items = state.rest,
                            key = { it.session.id }
                        ) { ceremony ->
                            CeremonyCard(ceremony = ceremony, onClick = { open(ceremony) })
                        }
                    }
                }
            }
        }
    }
}

/**
 * One ceremony, named after the key it is making.
 *
 * What a ceremony is *for* is the first thing to say about it, because a room
 * holding two holds them for different groups: one is this room's own key and
 * the other belongs to a subgroup that does not exist yet. A row that only said
 * how far along it was would leave a member guessing which of the two they were
 * looking at.
 */
@Composable
private fun CeremonyCard(
    ceremony: DkgRitualListUIState.Ceremony,
    onClick: () -> Unit,
) {
    val session = ceremony.session

    // Every colour inside this card is derived from the card rather than reached
    // for independently, because ListItem does not inherit LocalContentColor --
    // its headline comes from `onSurface` whatever the container is. See
    // ProposalListScreen, where that combination drew #1B1B1B on #1B1B1B.
    val cardColors = if (ceremony.awaitsYou) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    } else {
        CardDefaults.cardColors()
    }
    val cardContentColor = cardColors.contentColor

    // A subgroup is named by its proposal, and a member who did not open it may
    // be looking at the ceremony before that name has arrived -- hence the third
    // branch, which still says which of the two kinds of key this is.
    val subject = session.subject

    val label = when {
        !ceremony.isSubgroup -> stringResource(Res.string.the_groups_shared_key)
        subject != null -> stringResource(Res.string.a_subgroup_named_s, subject)
        else -> stringResource(Res.string.a_subgroups_key)
    }

    Card(onClick = onClick, colors = cardColors) {
        ListItem(
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent,
                headlineColor = cardContentColor,
                overlineColor = cardContentColor,
                supportingColor = cardContentColor,
                leadingIconColor = cardContentColor,
                trailingIconColor = cardContentColor,
            ),
            leadingContent = {
                Icon(
                    imageVector = when {
                        ceremony.awaitsYou -> Icons.Default.Key
                        session.stage == DkgRitualStage.COMPLETE -> Icons.Default.CheckCircle
                        session.stage == DkgRitualStage.FAILED -> Icons.Default.ErrorOutline
                        else -> Icons.Default.HourglassEmpty
                    },
                    // Carries the stage on its own, and carries it alone on the
                    // highlighted card where the failure state has no colour to
                    // spare.
                    contentDescription = when {
                        ceremony.awaitsYou -> "Waiting on you"
                        session.stage == DkgRitualStage.COMPLETE -> "Finished"
                        session.stage == DkgRitualStage.FAILED -> "Abandoned"
                        else -> "Waiting on others"
                    },
                    tint = when {
                        // `error` is 2.67:1 on the highlighted card, so there the
                        // failure is carried by the icon's shape and by the status
                        // line rather than by colour.
                        session.stage == DkgRitualStage.FAILED &&
                            !ceremony.awaitsYou -> MaterialTheme.colorScheme.error
                        else -> cardContentColor
                    }
                )
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // The same word the transcript's notice uses for the same
                    // thing, so a member who came here looking for the card they
                    // tapped finds it saying what it said there.
                    Text(
                        text = if (ceremony.awaitsYou) {
                            stringResource(Res.string.review)
                        } else {
                            stringResource(Res.string.open)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = cardContentColor
                    )

                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "Open this ceremony"
                    )
                }
            },
            overlineContent = { Text(text = label) },
            headlineContent = {
                Text(
                    text = statusOf(ceremony),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space25)
                ) {
                    // The quorum a ceremony settles is fixed by it and cannot be
                    // changed afterwards, so it belongs on the row rather than
                    // only on the screen behind it.
                    Text(
                        text = stringResource(
                            Res.string.s_of_s_members_will_sign_with_it,
                            session.threshold,
                            session.participantCount
                        )
                    )

                    Text(
                        text = "${ceremony.coordinatorName} · " +
                            session.createdAt.toFormattedTimeAndDateString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }
}

/**
 * Where a ceremony has got to, in a line.
 *
 * Shorter than the ceremony screen's ladder, and it has to be: this is one row
 * of a list somebody is scanning, not the page they are acting on. What it must
 * never do is call a ceremony finished when it is not, or waiting when there is
 * nothing left to wait for.
 *
 * What it owes this member outranks the stage, in the words the approval screens
 * use for the same step, so nobody is asked twice in two vocabularies.
 */
@Composable
private fun statusOf(ceremony: DkgRitualListUIState.Ceremony): String {
    ceremony.pendingApproval?.let {
        return when (it) {
            DkgApprovalStep.HOST_KEY ->
                stringResource(Res.string.join_the_ceremony_by_publishing_your_key)
            DkgApprovalStep.ROUND_1 ->
                stringResource(Res.string.send_your_contribution_to_the_key)
            DkgApprovalStep.ROUND_2 ->
                stringResource(Res.string.check_the_combined_result)
        }
    }

    if (ceremony.roomAwaitingCreation != null) {
        return stringResource(Res.string.the_group_has_its_key_finish_setting_it_up)
    }

    return when (ceremony.session.stage) {
        DkgRitualStage.COLLECTING_HOST_KEYS ->
            stringResource(Res.string.waiting_for_members_to_join)

        DkgRitualStage.COLLECTING_ROUND_1 ->
            stringResource(Res.string.waiting_for_everyone_to_commit_their_part)

        DkgRitualStage.COLLECTING_ROUND_2 ->
            stringResource(Res.string.waiting_for_everyone_to_confirm_the_result)

        DkgRitualStage.COMPLETE ->
            stringResource(Res.string.the_group_has_a_key_from_this_ceremony)

        // The reason is the whole content of a failure -- "a member refused" and
        // "the group could not agree" are different things to have happened -- so
        // it stands in for the bare word whenever there is one.
        DkgRitualStage.FAILED ->
            ceremony.session.failureReason
                ?: stringResource(Res.string.abandoned_no_key_was_created)
    }
}

@ConformancePreviews
@Composable
private fun DkgRitualListScreenPreview() {
    val session = DkgSession(
        id = "sessionId",
        chatRoomId = "chatRoomId",
        coordinatorPublicKey = "c".repeat(64),
        userPublicKey = "u".repeat(64),
        threshold = 2,
        participantCount = 3,
        hostPublicKey = "h".repeat(64),
        round1Random = "1".repeat(64),
        round2AuxRandom = "2".repeat(64),
    )

    MantraTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            DkgRitualListScreen(
                activeUserPublicKey = "u".repeat(64),
                chatRoomId = "chatRoomId",
                initialDkgRitualListUIState = DkgRitualListUIState.Loaded(
                    localChatRoom = LocalChatRoom(
                        chatRoom = ChatRoom(
                            id = "chatRoomId",
                            userPublicKey = "u".repeat(64),
                            subject = "#Ekklesia",
                            description = null,
                            initialGiftWrapPayloadId = "sdfaer",
                            mlsGroupState = null
                        )
                    ),
                    ceremonies = listOf(
                        DkgRitualListUIState.Ceremony(
                            session = session.copy(
                                id = "subgroupSessionId",
                                parentChatRoomId = "parentChatRoomId",
                                subject = "Translators",
                                stage = DkgRitualStage.COLLECTING_ROUND_1
                            ),
                            coordinatorName = "Ada",
                            pendingApproval = DkgApprovalStep.ROUND_1
                        ),
                        DkgRitualListUIState.Ceremony(
                            session = session.copy(stage = DkgRitualStage.COMPLETE),
                            coordinatorName = "You"
                        )
                    )
                ),
                chatRepository = ChatRepository.NO_OP_CHAT_REPOSITORY,
                dkgRepository = DkgRepository.NO_OP_DKG_REPOSITORY,
                onNavigateBack = {},
                onNavigateToRoute = {}
            )
        }
    }
}
