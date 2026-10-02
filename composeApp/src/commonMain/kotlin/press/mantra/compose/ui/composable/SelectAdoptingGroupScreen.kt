package press.mantra.compose.ui.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import mantra.composeapp.generated.resources.Res
import mantra.composeapp.generated.resources.asked_that_group_to_adopt_this_one
import mantra.composeapp.generated.resources.no_group_here_can_adopt_this_one
import mantra.composeapp.generated.resources.which_group_should_adopt_this_one
import mantra.composeapp.generated.resources.who_adopts_this_group
import org.jetbrains.compose.resources.stringResource
import press.mantra.compose.repository.ChatRepository
import press.mantra.compose.ui.composable.widgets.Decorative
import press.mantra.compose.ui.composable.widgets.EmptyState
import press.mantra.compose.ui.composable.widgets.ErrorState
import press.mantra.compose.ui.composable.widgets.LoadingDataIndicator
import press.mantra.compose.ui.composable.widgets.LocalSnackbarHostState
import press.mantra.compose.ui.composable.widgets.NavigateBackButton
import press.mantra.compose.ui.composable.widgets.rememberNotifier
import press.mantra.compose.ui.composable.widgets.ScreenStateTransition
import press.mantra.compose.ui.theme.readableContent
import press.mantra.compose.ui.theme.spacing
import press.mantra.compose.ui.view.model.SelectAdoptingGroupViewModel
import press.mantra.compose.ui.view.state.SelectAdoptingGroupUIState

/**
 * Which group to ask to adopt this one.
 *
 * Tapping a row asks that group's quorum to certify this room as its child. That
 * is an **offer** and nothing more: this room becomes their subgroup only once
 * its own quorum re-announces its key state naming them, which is a card back on
 * the room's own screen. So the snackbar says what was *asked* rather than what
 * was done.
 *
 * **It does not navigate away on success**, and that is the fix for two things at
 * once. A message launched here while the screen was being popped was racing its
 * own scope's cancellation; and the list is the better confirmation anyway -- the
 * row that was just asked leaves it, because a certificate now stands for the
 * pair and `refuseAdoption` says so. The way out is the back button, which a
 * pushed screen has to have regardless.
 *
 * The list is the profile's own rooms -- see `SelectAdoptingGroupViewModel` for
 * why it can be nothing else -- so empty is an ordinary answer and the empty
 * state explains it rather than apologising.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectAdoptingGroupScreen(
    activeUserPublicKey: HexKey,
    chatRoomId: String,
    initialUIState: SelectAdoptingGroupUIState = SelectAdoptingGroupUIState.Loading,
    chatRepository: ChatRepository,
    onNavigateBack: () -> Unit,
) {
    val viewModel: SelectAdoptingGroupViewModel = viewModel(
        factory = SelectAdoptingGroupViewModel.factory(
            activeUserPublicKey = activeUserPublicKey,
            chatRoomId = chatRoomId,
            initialUIState = initialUIState,
            chatRepository = chatRepository
        )
    )

    LaunchedEffect(chatRoomId) { viewModel.initiate() }

    val snackbarHostState = LocalSnackbarHostState.current

    // Both read outside the handler, because both are composable and an onClick
    // lambda is not.
    val notify = rememberNotifier(rememberCoroutineScope())
    val asked = stringResource(Res.string.asked_that_group_to_adopt_this_one)

    ScreenStateTransition(viewModel.uiState) { uiState ->
        when (val state = uiState) {
            is SelectAdoptingGroupUIState.Error -> ErrorState(
                message = state.message,
                // Retrying reloads the group and the candidates, which is what
                // failed.
                onRetry = { viewModel.initiate() }
            )

            SelectAdoptingGroupUIState.Loading -> LoadingDataIndicator()

            is SelectAdoptingGroupUIState.Loaded -> Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(Res.string.who_adopts_this_group)) },
                        navigationIcon = { NavigateBackButton(onNavigateBack) },
                    )
                }
            ) { innerPadding ->
                LazyColumn(
                    modifier = Modifier
                        .padding(innerPadding)
                        .readableContent()
                        .fillMaxSize()
                        .padding(MaterialTheme.spacing.containerPadding),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.itemGap)
                ) {
                    if (state.candidates.isEmpty()) {
                        item {
                            // The message carries the reason, because the
                            // reason is the whole content of this absence: a
                            // group cannot be offered as a parent by somebody
                            // who is not in it, so the remedy is a person.
                            EmptyState(
                                message = stringResource(
                                    Res.string.no_group_here_can_adopt_this_one
                                )
                            )
                        }
                    } else {
                        item {
                            Text(
                                text = stringResource(
                                    Res.string.which_group_should_adopt_this_one
                                ),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        items(
                            items = state.candidates,
                            key = { candidate -> candidate.chatRoom.id }
                        ) { candidate ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    // `offerTo` is not suspending and calls back
                                    // on the main thread, so there is nothing to
                                    // launch around it.
                                    viewModel.offerTo(candidate.chatRoom.id) { message ->
                                        notify(message ?: asked)
                                    }
                                }
                            ) {
                                ListItem(
                                    leadingContent = {
                                        Icon(
                                            Icons.Default.AccountTree,
                                            contentDescription = Decorative
                                        )
                                    },
                                    trailingContent = {
                                        Icon(
                                            Icons.Default.ChevronRight,
                                            contentDescription = Decorative
                                        )
                                    },
                                    headlineContent = {
                                        Text(
                                            text = candidate.chatRoom.subject
                                                ?: candidate.chatRoom.id.take(16),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    supportingContent = {
                                        Text(
                                            text = candidate.chatRoom.id.take(16),
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 1,
                                            overflow = TextOverflow.MiddleEllipsis
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
}
