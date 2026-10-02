package press.mantra.compose.ui.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import mantra.composeapp.generated.resources.Res
import mantra.composeapp.generated.resources.add_an_owner
import mantra.composeapp.generated.resources.an_owner_is_a_64_character_public_key
import mantra.composeapp.generated.resources.describe_the_project
import mantra.composeapp.generated.resources.eg_the_four_gospels_into_sesotho
import mantra.composeapp.generated.resources.name_of_project
import mantra.composeapp.generated.resources.owned_by_this_group
import mantra.composeapp.generated.resources.owner_public_key
import mantra.composeapp.generated.resources.owners
import mantra.composeapp.generated.resources.project_detail
import mantra.composeapp.generated.resources.propose_project
import mantra.composeapp.generated.resources.remove_owner
import mantra.composeapp.generated.resources.source_versions
import mantra.composeapp.generated.resources.start_a_project_in_this_group
import mantra.composeapp.generated.resources.this_group_has_no_library_to_build_a_project_from
import mantra.composeapp.generated.resources.translations
import org.jetbrains.compose.resources.stringResource
import press.mantra.compose.extensions.shortened
import press.mantra.compose.repository.ChatRepository
import press.mantra.compose.repository.FrostSigningRepository
import press.mantra.compose.repository.MantraRepository
import press.mantra.compose.repository.NostrRepository
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
import press.mantra.compose.ui.view.model.AddProjectViewModel
import press.mantra.compose.ui.view.state.AddProjectUIState

/**
 * Proposing a project: a name, what it is, who owns it, and what is in it.
 *
 * The creator is not a field. It is whoever is filling the form in, the view
 * model already knows it, and a picker would only let somebody credit the wrong
 * person for work the group is about to put its signature to.
 *
 * The owners are a field, pre-filled with this group. Most projects never touch
 * it, and that is the point of pre-filling rather than hiding: a group making
 * something for somebody else needs the question asked, and a group that is not
 * needs to see the answer it is giving.
 */
@OptIn(
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class
)
@Composable
fun AddProjectScreen(
    activeUserPublicKey: HexKey,
    chatRoomId: String,
    relayHint: String?,
    initialAddProjectUIState: AddProjectUIState = AddProjectUIState.Loading,
    nostrRepository: NostrRepository,
    chatRepository: ChatRepository,
    mantraRepository: MantraRepository,
    frostSigningRepository: FrostSigningRepository,
    onNavigateToRouteAndPopUpInclusive: (Route) -> Unit,
    onNavigateToRoute: (Route) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val addProjectViewModel: AddProjectViewModel = viewModel(
        factory = AddProjectViewModel.factory(
            chatRoomId = chatRoomId,
            relayHint = relayHint,
            initialAddProjectUIState = initialAddProjectUIState,
            nostrRepository = nostrRepository,
            chatRepository = chatRepository,
            activeUserPublicKey = activeUserPublicKey,
            frostSigningRepository = frostSigningRepository,
            mantraRepository = mantraRepository,
        )
    )

    ScreenStateTransition(addProjectViewModel.addProjectUIState) { uiState ->
        when (val addProjectUIState = uiState) {
            is AddProjectUIState.Error -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(MaterialTheme.spacing.space600))
                    Text(text = addProjectUIState.message)
                }
            }

            is AddProjectUIState.Loaded -> {
                val nameFieldState = rememberTextFieldState()
                val descriptionFieldState = rememberTextFieldState()
                val ownerFieldState = rememberTextFieldState()

                // Pre-filled with the room, which is the common answer and a real
                // value rather than an absence: "owned by us" and "owner unstated"
                // must not be the same event.
                val owners: SnapshotStateList<HexKey> = remember {
                    mutableStateListOf(chatRoomId)
                }
                val selectedSourceVersions = remember { mutableStateListOf<HexKey>() }
                val selectedTranslations = remember { mutableStateListOf<HexKey>() }

                var nameEntered by remember { mutableStateOf(false) }
                LaunchedEffect(nameFieldState.text) {
                    nameEntered = nameFieldState.text.isNotBlank()
                }

                // A key that is not 64 hex characters is dropped by
                // ContributorTag.parse, so it would vanish between this form and
                // the signature. Caught on entry instead.
                val typedOwner = ownerFieldState.text.toString().trim()
                val canAddOwner = typedOwner.length == OWNER_KEY_LENGTH &&
                    typedOwner !in owners

                val canAddProject = nameEntered &&
                    owners.isNotEmpty() &&
                    addProjectUIState.canSign

                // imePadding: this screen has text fields, and without it the
                // software keyboard covers whatever is being typed into.
                Scaffold(
                    snackbarHost = { SnackbarHost(LocalSnackbarHostState.current) },
                    modifier = Modifier.imePadding(),
                    topBar = {
                        TopAppBar(
                            title = {
                                addProjectUIState.localChatRoom.RenderChatRoomTitleText(activeUserPublicKey)
                            },
                            navigationIcon = { NavigateBackButton(onNavigateBack) },
                        )
                    },
                    bottomBar = {
                        BottomAppBar(
                            actions = {},
                            floatingActionButton = {
                                ExtendedFab(
                                    label = stringResource(Res.string.propose_project),
                                    icon = {
                                        Icon(Icons.Default.Add, contentDescription = Decorative)
                                    },
                                    enabled = canAddProject,
                                    onClick = {
                                        addProjectViewModel.addProject(
                                            localChatRoom = addProjectUIState.localChatRoom,
                                            nameField = nameFieldState,
                                            descriptionField = descriptionFieldState,
                                            ownerPublicKeys = owners.toSet(),
                                            sourceVersionIds = selectedSourceVersions.toList(),
                                            translationIds = selectedTranslations.toList(),
                                            onSuccess = { sessionId ->
                                                // Onto the session rather than to
                                                // the project. Nothing exists yet
                                                // -- it appears when enough members
                                                // sign -- so a detail screen for a
                                                // row that is not there would read
                                                // as a failure.
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
                                                    ImplementationPendingRoute("Failed project")
                                                )
                                            }
                                        )
                                    }
                                )
                            }
                        )
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .readableContent()
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(
                            MaterialTheme.spacing.space125
                        ),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(stringResource(Res.string.start_a_project_in_this_group))

                        if (!addProjectUIState.canSign) {
                            Text(
                                text = "This group has no shared key, so it cannot sign a " +
                                    "project into being. Run a shared key ceremony first.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        OutlinedTextField(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BottomAppBarDefaults.containerColor),
                            state = nameFieldState,
                            colors = transparentOutline(),
                            leadingIcon = {
                                Icon(
                                    Icons.Default.DriveFileRenameOutline,
                                    contentDescription = Decorative
                                )
                            },
                            label = { Text(stringResource(Res.string.name_of_project)) },
                            placeholder = {
                                Text(stringResource(Res.string.eg_the_four_gospels_into_sesotho))
                            },
                        )

                        OutlinedTextField(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BottomAppBarDefaults.containerColor),
                            state = descriptionFieldState,
                            colors = transparentOutline(),
                            leadingIcon = {
                                Icon(Icons.Default.Notes, contentDescription = Decorative)
                            },
                            label = { Text(stringResource(Res.string.describe_the_project)) },
                        )

                        // --- owners ---

                        Text(
                            modifier = Modifier.fillMaxWidth(),
                            text = stringResource(Res.string.owners),
                            style = MaterialTheme.typography.labelMedium
                        )

                        val thisGroup = stringResource(Res.string.owned_by_this_group)

                        owners.forEach { owner ->
                            ListItem(
                                headlineContent = {
                                    Text(
                                        if (owner == chatRoomId) thisGroup else owner.shortened()
                                    )
                                },
                                trailingContent = {
                                    // The last one cannot go: ProjectEvent.build
                                    // refuses an empty set, and a button that
                                    // throws is worse than one that is off.
                                    IconButton(
                                        enabled = owners.size > 1,
                                        onClick = { owners.remove(owner) }
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = stringResource(
                                                Res.string.remove_owner
                                            )
                                        )
                                    }
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(BottomAppBarDefaults.containerColor),
                                state = ownerFieldState,
                                colors = transparentOutline(),
                                label = { Text(stringResource(Res.string.owner_public_key)) },
                                isError = typedOwner.isNotEmpty() && !canAddOwner,
                                supportingText = {
                                    if (typedOwner.isNotEmpty() && !canAddOwner) {
                                        Text(
                                            stringResource(
                                                Res.string.an_owner_is_a_64_character_public_key
                                            )
                                        )
                                    }
                                },
                            )

                            IconButton(
                                enabled = canAddOwner,
                                onClick = {
                                    owners.add(typedOwner)
                                    ownerFieldState.clearText()
                                }
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = stringResource(Res.string.add_an_owner)
                                )
                            }
                        }

                        // --- what is in it ---

                        if (addProjectUIState.sourceVersions.isEmpty()) {
                            Text(
                                text = stringResource(
                                    Res.string.this_group_has_no_library_to_build_a_project_from
                                ),
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            Text(
                                modifier = Modifier.fillMaxWidth(),
                                text = stringResource(Res.string.source_versions),
                                style = MaterialTheme.typography.labelMedium
                            )

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(
                                    MaterialTheme.spacing.itemGap
                                )
                            ) {
                                addProjectUIState.sourceVersions.forEach { version ->
                                    FilterChip(
                                        selected = version.id in selectedSourceVersions,
                                        onClick = {
                                            if (version.id in selectedSourceVersions) {
                                                selectedSourceVersions.remove(version.id)
                                            } else {
                                                selectedSourceVersions.add(version.id)
                                            }
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Article,
                                                contentDescription = Decorative
                                            )
                                        },
                                        label = { Text(version.versionLabel) }
                                    )
                                }
                            }
                        }

                        if (addProjectUIState.translations.isNotEmpty()) {
                            Text(
                                modifier = Modifier.fillMaxWidth(),
                                text = stringResource(Res.string.translations),
                                style = MaterialTheme.typography.labelMedium
                            )

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(
                                    MaterialTheme.spacing.itemGap
                                )
                            ) {
                                addProjectUIState.translations.forEach { translation ->
                                    FilterChip(
                                        selected = translation.id in selectedTranslations,
                                        onClick = {
                                            if (translation.id in selectedTranslations) {
                                                selectedTranslations.remove(translation.id)
                                            } else {
                                                selectedTranslations.add(translation.id)
                                            }
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Translate,
                                                contentDescription = Decorative
                                            )
                                        },
                                        label = { Text(translation.name) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            AddProjectUIState.Loading -> {
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
        if (initialAddProjectUIState == AddProjectUIState.Loading) {
            addProjectViewModel.initiateAddProject()
        }
    }
}

/** What `ContributorTag.parse` demands of an owner's value. */
private const val OWNER_KEY_LENGTH = 64

/** The borderless field the rest of this family's forms use. */
@Composable
private fun transparentOutline() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color.Transparent,
    unfocusedBorderColor = Color.Transparent,
    disabledBorderColor = Color.Transparent
)

@ConformancePreviews
@Composable
private fun AddProjectScreenPreview() {
    MantraTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            AddProjectScreen(
                activeUserPublicKey = "a".repeat(64),
                chatRoomId = "b".repeat(64),
                relayHint = null,
                nostrRepository = NostrRepository.NO_OP_NOSTR_REPOSITORY,
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
