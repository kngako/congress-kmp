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
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import mantra.composeapp.generated.resources.Res
import mantra.composeapp.generated.resources.created_by
import mantra.composeapp.generated.resources.details
import mantra.composeapp.generated.resources.no_source_versions_in_this_project
import mantra.composeapp.generated.resources.no_translations_in_this_project
import mantra.composeapp.generated.resources.owned_by_this_group
import mantra.composeapp.generated.resources.owners
import mantra.composeapp.generated.resources.project_detail
import mantra.composeapp.generated.resources.source_versions
import mantra.composeapp.generated.resources.translations
import mantra.composeapp.generated.resources.version
import org.jetbrains.compose.resources.stringResource
import press.mantra.compose.database.model.Profile
import press.mantra.compose.extensions.shortened
import press.mantra.compose.repository.FrostSigningRepository
import press.mantra.compose.repository.MantraRepository
import press.mantra.compose.repository.NostrRepository
import press.mantra.compose.ui.composable.navigation.routes.Route
import press.mantra.compose.ui.composable.navigation.routes.TranslationArtifactVersionDetailRoute
import press.mantra.compose.ui.composable.widgets.Decorative
import press.mantra.compose.ui.composable.widgets.DetailRow
import press.mantra.compose.ui.composable.widgets.LoadingDataIndicator
import press.mantra.compose.ui.composable.widgets.LocalSnackbarHostState
import press.mantra.compose.ui.composable.widgets.NavigateBackButton
import press.mantra.compose.ui.composable.widgets.ScreenStateTransition
import press.mantra.compose.ui.theme.ConformancePreviews
import press.mantra.compose.ui.theme.MantraTheme
import press.mantra.compose.ui.theme.readableContent
import press.mantra.compose.ui.theme.spacing
import press.mantra.compose.ui.view.model.ProjectDetailViewModel
import press.mantra.compose.ui.view.state.ProjectDetailUIState

/**
 * What a body of work is, and who it belongs to.
 *
 * The two people rows are not decoration. A project's creator and owners are
 * claims the group put its signature to -- nobody else checks them, and the
 * event is the only place they exist -- so this screen is where a member reads
 * what was agreed after the fact.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    activeUserPublicKey: HexKey,
    projectId: String,
    chatRoomId: String,
    relayHint: String?,
    initialProjectDetailUIState: ProjectDetailUIState = ProjectDetailUIState.Loading,
    mantraRepository: MantraRepository,
    nostrRepository: NostrRepository,
    frostSigningRepository: FrostSigningRepository,
    onNavigateToRoute: (Route) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val projectDetailViewModel: ProjectDetailViewModel = viewModel(
        factory = ProjectDetailViewModel.factory(
            activeUserPublicKey = activeUserPublicKey,
            projectId = projectId,
            chatRoomId = chatRoomId,
            relayHint = relayHint,
            initialProjectDetailUIState = initialProjectDetailUIState,
            mantraRepository = mantraRepository,
            nostrRepository = nostrRepository,
            frostSigningRepository = frostSigningRepository,
        )
    )

    ScreenStateTransition(projectDetailViewModel.projectDetailUIState) { uiState ->
        when (val projectDetailUIState = uiState) {
            is ProjectDetailUIState.Error -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(MaterialTheme.spacing.space600))
                    Text(text = projectDetailUIState.message)
                }
            }

            is ProjectDetailUIState.Loaded -> {
                val project = projectDetailUIState.project

                Scaffold(
                    snackbarHost = { SnackbarHost(LocalSnackbarHostState.current) },
                    topBar = {
                        TopAppBar(
                            title = { Text(project.name) },
                            navigationIcon = { NavigateBackButton(onNavigateBack) },
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
                        if (project.description.isNotBlank()) {
                            item { Text(project.description) }
                            item { HorizontalDivider() }
                        }

                        // Source versions
                        item {
                            Text(
                                text = stringResource(Res.string.source_versions),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        if (projectDetailUIState.sourceVersions.isEmpty()) {
                            item {
                                Text(stringResource(Res.string.no_source_versions_in_this_project))
                            }
                        } else {
                            items(
                                items = projectDetailUIState.sourceVersions,
                                key = { version -> version.id }
                            ) { version ->
                                Card {
                                    ListItem(
                                        leadingContent = {
                                            Icon(
                                                Icons.Default.Article,
                                                contentDescription = Decorative
                                            )
                                        },
                                        overlineContent = {
                                            Text(stringResource(Res.string.version))
                                        },
                                        headlineContent = { Text(version.versionLabel) }
                                    )
                                }
                            }
                        }
                        item { HorizontalDivider() }

                        // Translations
                        item {
                            Text(
                                text = stringResource(Res.string.translations),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        if (projectDetailUIState.translations.isEmpty()) {
                            item {
                                Text(stringResource(Res.string.no_translations_in_this_project))
                            }
                        } else {
                            items(
                                items = projectDetailUIState.translations,
                                key = { translation -> translation.id }
                            ) { translation ->
                                Card(
                                    onClick = {
                                        onNavigateToRoute.invoke(
                                            TranslationArtifactVersionDetailRoute(
                                                activeUserPublicKey = activeUserPublicKey,
                                                translationArtifactVersionId = translation.id,
                                                chatRoomId = chatRoomId,
                                                relayHint = relayHint
                                            )
                                        )
                                    }
                                ) {
                                    ListItem(
                                        leadingContent = {
                                            Icon(
                                                Icons.Default.Translate,
                                                contentDescription = Decorative
                                            )
                                        },
                                        headlineContent = { Text(translation.name) }
                                    )
                                }
                            }
                        }
                        item { HorizontalDivider() }

                        // Who it belongs to
                        item {
                            Text(
                                text = stringResource(Res.string.details),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        item {
                            val thisGroup = stringResource(Res.string.owned_by_this_group)

                            Card {
                                DetailRow(
                                    label = stringResource(Res.string.created_by),
                                    value = describe(
                                        publicKey = project.creatorPublicKey,
                                        people = projectDetailUIState.people,
                                        chatRoomId = chatRoomId,
                                        thisGroup = thisGroup,
                                    )
                                )

                                // The room first, so the ordinary single-owner
                                // case is one unremarkable line and an outside
                                // owner is the one that stands out.
                                project.ownerPublicKeySet()
                                    .sortedBy { if (it == chatRoomId) 0 else 1 }
                                    .forEach { owner ->
                                        DetailRow(
                                            label = stringResource(Res.string.owners),
                                            value = describe(
                                                publicKey = owner,
                                                people = projectDetailUIState.people,
                                                chatRoomId = chatRoomId,
                                                thisGroup = thisGroup,
                                            )
                                        )
                                    }
                            }
                        }
                    }
                }
            }

            ProjectDetailUIState.Loading -> {
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
        if (initialProjectDetailUIState == ProjectDetailUIState.Loading) {
            projectDetailViewModel.initiateProjectDetail()
        }
    }
}

/**
 * A key as a person, as this group, or as a short hash -- in that order.
 *
 * The room's own key reads as the group rather than as its id: a member
 * standing inside the room does not need it spelled back at them, and "this
 * group" is what the owner actually is.
 */
private fun describe(
    publicKey: HexKey,
    people: Map<HexKey, Profile>,
    chatRoomId: String,
    thisGroup: String,
): String = when {
    publicKey == chatRoomId -> thisGroup
    else -> people[publicKey]?.humanReadableNameOrPubkey() ?: publicKey.shortened()
}

@ConformancePreviews
@Composable
private fun ProjectDetailScreenPreview() {
    MantraTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            ProjectDetailScreen(
                activeUserPublicKey = "a".repeat(64),
                projectId = "p".repeat(64),
                chatRoomId = "b".repeat(64),
                relayHint = null,
                mantraRepository = MantraRepository.NO_OP_MANTRA_REPOSITORY,
                nostrRepository = NostrRepository.NO_OP_NOSTR_REPOSITORY,
                frostSigningRepository = FrostSigningRepository.NO_OP_FROST_SIGNING_REPOSITORY,
                onNavigateToRoute = {},
                onNavigateBack = {},
            )
        }
    }
}
