package press.mantra.compose.ui.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import mantra.composeapp.generated.resources.Res
import mantra.composeapp.generated.resources.add_source_version_to_project
import mantra.composeapp.generated.resources.add_translation_to_project
import mantra.composeapp.generated.resources.every_translation_is_already_in_this_project
import mantra.composeapp.generated.resources.everything_in_the_library_is_already_in_this_project
import mantra.composeapp.generated.resources.project_detail
import mantra.composeapp.generated.resources.propose_adding_to_the_project
import org.jetbrains.compose.resources.stringResource
import press.mantra.compose.repository.ChatRepository
import press.mantra.compose.repository.FrostSigningRepository
import press.mantra.compose.repository.MantraRepository
import press.mantra.compose.ui.composable.navigation.routes.FrostSigningRoute
import press.mantra.compose.ui.composable.navigation.routes.ImplementationPendingRoute
import press.mantra.compose.ui.composable.navigation.routes.Route
import press.mantra.compose.ui.composable.widgets.Decorative
import press.mantra.compose.ui.composable.widgets.LoadingDataIndicator
import press.mantra.compose.ui.composable.widgets.LocalSnackbarHostState
import press.mantra.compose.ui.composable.widgets.NavigateBackButton
import press.mantra.compose.ui.composable.widgets.ScreenStateTransition
import press.mantra.compose.ui.composable.widgets.buttons.ExtendedFab
import press.mantra.compose.ui.theme.ConformancePreviews
import press.mantra.compose.ui.theme.MantraTheme
import press.mantra.compose.ui.theme.readableContent
import press.mantra.compose.ui.theme.spacing
import press.mantra.compose.ui.view.model.AddToProjectViewModel
import press.mantra.compose.ui.view.state.AddToProjectUIState

/**
 * One more piece of work into a project that already exists.
 *
 * Unlike founding a project, this proposes a single membership -- so the whole
 * of what the group is asked to agree to is two ids. That is why
 * `FrostSigningViewModel` resolves names for them: without it the other members
 * are shown a truncated hash and asked to sign it, which is not a question
 * anybody can answer.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AddToProjectScreen(
    activeUserPublicKey: HexKey,
    projectId: String,
    chatRoomId: String,
    translations: Boolean,
    relayHint: String?,
    initialAddToProjectUIState: AddToProjectUIState = AddToProjectUIState.Loading,
    chatRepository: ChatRepository,
    mantraRepository: MantraRepository,
    frostSigningRepository: FrostSigningRepository,
    onNavigateToRouteAndPopUpInclusive: (Route) -> Unit,
    onNavigateToRoute: (Route) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val addToProjectViewModel: AddToProjectViewModel = viewModel(
        factory = AddToProjectViewModel.factory(
            activeUserPublicKey = activeUserPublicKey,
            projectId = projectId,
            chatRoomId = chatRoomId,
            translations = translations,
            relayHint = relayHint,
            initialAddToProjectUIState = initialAddToProjectUIState,
            chatRepository = chatRepository,
            mantraRepository = mantraRepository,
            frostSigningRepository = frostSigningRepository,
        )
    )

    ScreenStateTransition(addToProjectViewModel.addToProjectUIState) { uiState ->
        when (val addToProjectUIState = uiState) {
            is AddToProjectUIState.Error -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(MaterialTheme.spacing.space600))
                    Text(text = addToProjectUIState.message)
                }
            }

            is AddToProjectUIState.Loaded -> {
                var selectedId: HexKey? by remember { mutableStateOf(null) }

                Scaffold(
                    snackbarHost = { SnackbarHost(LocalSnackbarHostState.current) },
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    if (translations) {
                                        stringResource(Res.string.add_translation_to_project)
                                    } else {
                                        stringResource(Res.string.add_source_version_to_project)
                                    }
                                )
                            },
                            navigationIcon = { NavigateBackButton(onNavigateBack) },
                        )
                    },
                    bottomBar = {
                        BottomAppBar(
                            actions = {},
                            floatingActionButton = {
                                ExtendedFab(
                                    label = stringResource(
                                        Res.string.propose_adding_to_the_project
                                    ),
                                    icon = {
                                        Icon(Icons.Default.Add, contentDescription = Decorative)
                                    },
                                    enabled = selectedId != null && addToProjectUIState.canSign,
                                    onClick = {
                                        val chosen = selectedId ?: return@ExtendedFab

                                        addToProjectViewModel.addToProject(
                                            localChatRoom = addToProjectUIState.localChatRoom,
                                            selectedId = chosen,
                                            onSuccess = { sessionId ->
                                                onNavigateToRouteAndPopUpInclusive.invoke(
                                                    FrostSigningRoute(
                                                        activeUserPublicKey = activeUserPublicKey,
                                                        chatRoomId = chatRoomId,
                                                        sessionId = sessionId
                                                    )
                                                )
                                            },
                                            onFailure = {
                                                onNavigateToRoute.invoke(
                                                    ImplementationPendingRoute(
                                                        "Failed project membership"
                                                    )
                                                )
                                            }
                                        )
                                    }
                                )
                            }
                        )
                    }
                ) { innerPadding ->
                    LazyColumn(
                        modifier = Modifier
                            .padding(innerPadding)
                            .readableContent()
                            .fillMaxSize()
                            .padding(MaterialTheme.spacing.space250),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space125)
                    ) {
                        item { Text(addToProjectUIState.project.name) }

                        if (!addToProjectUIState.canSign) {
                            item {
                                Text(
                                    text = "This group has no shared key, so it cannot sign " +
                                        "anything into this project. Run a shared key " +
                                        "ceremony first.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        if (addToProjectUIState.candidates.isEmpty()) {
                            item {
                                Text(
                                    if (translations) {
                                        stringResource(
                                            Res.string
                                                .every_translation_is_already_in_this_project
                                        )
                                    } else {
                                        stringResource(
                                            Res.string
                                                .everything_in_the_library_is_already_in_this_project
                                        )
                                    }
                                )
                            }
                        } else {
                            items(
                                items = addToProjectUIState.candidates,
                                key = { candidate -> candidate.id }
                            ) { candidate ->
                                Card(onClick = { selectedId = candidate.id }) {
                                    ListItem(
                                        leadingContent = {
                                            Icon(
                                                if (translations) {
                                                    Icons.Default.Translate
                                                } else {
                                                    Icons.Default.Article
                                                },
                                                contentDescription = Decorative
                                            )
                                        },
                                        trailingContent = {
                                            // One at a time: the proposal is one
                                            // event, and a quorum per thing added
                                            // is the grain the group signs at.
                                            RadioButton(
                                                selected = selectedId == candidate.id,
                                                onClick = { selectedId = candidate.id }
                                            )
                                        },
                                        headlineContent = { Text(candidate.label) },
                                        supportingContent = candidate.detail?.let { detail ->
                                            { Text(detail) }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            AddToProjectUIState.Loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MaterialTheme.spacing.space250),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space250)
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = stringResource(Res.string.project_detail),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    LoadingDataIndicator(fillScreen = false)
                    Spacer(modifier = Modifier.weight(2f))
                }
            }
        }
    }

    LaunchedEffect(true) {
        if (initialAddToProjectUIState == AddToProjectUIState.Loading) {
            addToProjectViewModel.initiateAddToProject()
        }
    }
}

@ConformancePreviews
@Composable
private fun AddToProjectScreenPreview() {
    MantraTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            AddToProjectScreen(
                activeUserPublicKey = "a".repeat(64),
                projectId = "p".repeat(64),
                chatRoomId = "b".repeat(64),
                translations = false,
                relayHint = null,
                chatRepository = ChatRepository.NO_OP_CHAT_REPOSITORY,
                mantraRepository = MantraRepository.NO_OP_MANTRA_REPOSITORY,
                frostSigningRepository = FrostSigningRepository.NO_OP_FROST_SIGNING_REPOSITORY,
                onNavigateToRouteAndPopUpInclusive = {},
                onNavigateToRoute = {},
                onNavigateBack = {},
            )
        }
    }
}
