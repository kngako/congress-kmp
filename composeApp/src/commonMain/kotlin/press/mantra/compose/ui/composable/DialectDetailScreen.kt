package press.mantra.compose.ui.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.LibraryBooks
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import mantra.composeapp.generated.resources.Res
import mantra.composeapp.generated.resources.country
import mantra.composeapp.generated.resources.details
import mantra.composeapp.generated.resources.dialect_detail
import mantra.composeapp.generated.resources.give_this_a_room_of_its_own
import mantra.composeapp.generated.resources.language
import mantra.composeapp.generated.resources.library
import mantra.composeapp.generated.resources.no_artifacts_are_written_in_this_dialect
import mantra.composeapp.generated.resources.no_translations_are_into_this_dialect
import mantra.composeapp.generated.resources.translations
import org.jetbrains.compose.resources.stringResource
import press.mantra.compose.database.model.MantraDialect
import press.mantra.compose.managers.SubgroupOrigin
import press.mantra.compose.repository.ChatRepository
import press.mantra.compose.repository.MantraRepository
import press.mantra.compose.ui.composable.navigation.routes.ArtifactDetailRoute
import press.mantra.compose.ui.composable.navigation.routes.Route
import press.mantra.compose.ui.composable.navigation.routes.SelectSubgroupAdminsRoute
import press.mantra.compose.ui.composable.navigation.routes.TranslationArtifactVersionDetailRoute
import press.mantra.compose.ui.composable.widgets.Decorative
import press.mantra.compose.ui.composable.widgets.DetailRow
import press.mantra.compose.ui.composable.widgets.EmptyState
import press.mantra.compose.ui.composable.widgets.ErrorState
import press.mantra.compose.ui.composable.widgets.LoadingDataIndicator
import press.mantra.compose.ui.composable.widgets.LocalSnackbarHostState
import press.mantra.compose.ui.composable.widgets.NavigateBackButton
import press.mantra.compose.ui.composable.widgets.ScreenStateTransition
import press.mantra.compose.ui.theme.ConformancePreviews
import press.mantra.compose.ui.theme.MantraTheme
import press.mantra.compose.ui.theme.readableContent
import press.mantra.compose.ui.theme.spacing
import press.mantra.compose.ui.view.model.DialectDetailViewModel
import press.mantra.compose.ui.view.state.DialectDetailUIState

/**
 * A dialect, and what the group has written in it.
 *
 * The two lists are the screen. A dialect is a name, a language code and a
 * country, and those three are at the bottom under the details the way they are
 * everywhere else; what a member opens a dialect for is the artifacts written in
 * it and the translations into it, which are the only two things in this app that
 * point at a dialect at all.
 *
 * Until this screen the dialect card in `ChatRoomDetailScreen` had no `onClick`,
 * which made a dialect the one entity in a room a member could not open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialectDetailScreen(
    activeUserPublicKey: HexKey,
    dialectId: String,
    chatRoomId: String,
    relayHint: String?,
    initialDialectDetailUIState: DialectDetailUIState = DialectDetailUIState.Loading,
    mantraRepository: MantraRepository,
    chatRepository: ChatRepository,
    onNavigateToRoute: (Route) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val dialectDetailViewModel: DialectDetailViewModel = viewModel(
        factory = DialectDetailViewModel.factory(
            activeUserPublicKey = activeUserPublicKey,
            dialectId = dialectId,
            chatRoomId = chatRoomId,
            relayHint = relayHint,
            initialDialectDetailUIState = initialDialectDetailUIState,
            mantraRepository = mantraRepository,
            chatRepository = chatRepository,
        )
    )

    ScreenStateTransition(dialectDetailViewModel.dialectDetailUIState) { uiState ->
        when (val dialectDetailUIState = uiState) {
            is DialectDetailUIState.Error -> ErrorState(
                message = dialectDetailUIState.message,
                // Reloading is worth offering even though the usual cause is a
                // dialect this device does not hold: a room's rows arrive on the
                // group's schedule, so the second attempt can genuinely find what
                // the first could not.
                onRetry = { dialectDetailViewModel.initiateDialectDetail() }
            )

            is DialectDetailUIState.Loaded -> {
                val dialect = dialectDetailUIState.dialect

                Scaffold(
                    snackbarHost = { SnackbarHost(LocalSnackbarHostState.current) },
                    topBar = {
                        TopAppBar(
                            title = { Text(dialect.name) },
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
                        verticalArrangement = Arrangement.spacedBy(
                            MaterialTheme.spacing.relatedGap
                        )
                    ) {
                        item {
                            Text(
                                text = stringResource(Res.string.library),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        if (dialectDetailUIState.artifacts.isEmpty()) {
                            item {
                                EmptyState(
                                    message = stringResource(
                                        Res.string.no_artifacts_are_written_in_this_dialect
                                    ),
                                    icon = Icons.Default.LibraryBooks,
                                )
                            }
                        } else {
                            items(
                                items = dialectDetailUIState.artifacts,
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
                                                contentDescription = Decorative
                                            )
                                        },
                                        headlineContent = { Text(artifact.name) },
                                        supportingContent = { Text(artifact.url) }
                                    )
                                }
                            }
                        }

                        item { HorizontalDivider() }

                        item {
                            Text(
                                text = stringResource(Res.string.translations),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        if (dialectDetailUIState.translations.isEmpty()) {
                            item {
                                EmptyState(
                                    message = stringResource(
                                        Res.string.no_translations_are_into_this_dialect
                                    ),
                                    icon = Icons.Default.Translate,
                                )
                            }
                        } else {
                            items(
                                items = dialectDetailUIState.translations,
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
                                        headlineContent = { Text(translation.name) },
                                        supportingContent = { Text(translation.visibility) }
                                    )
                                }
                            }
                        }

                        item { HorizontalDivider() }

                        item {
                            // Hidden rather than disabled where it cannot work,
                            // the way the room's own subgroup entry is: a device
                            // with no share of the key cannot open the
                            // certificate session at all, and a NIP-17 room has
                            // no admins to be a parent's.
                            if (dialectDetailUIState.canAddSubgroup) {
                                TextButton(
                                    onClick = {
                                        val candidate = SubgroupOrigin.of(dialect)

                                        onNavigateToRoute.invoke(
                                            SelectSubgroupAdminsRoute(
                                                activeUserPublicKey = activeUserPublicKey,
                                                parentChatRoomId = candidate.parentChatRoomId,
                                                relayHint = relayHint,
                                                name = candidate.name,
                                                originKind = candidate.origin.kind,
                                                originId = candidate.origin.id,
                                            )
                                        )
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.AccountTree,
                                        contentDescription = Decorative
                                    )
                                    Spacer(
                                        modifier = Modifier.width(
                                            MaterialTheme.spacing.itemGap
                                        )
                                    )
                                    Text(
                                        stringResource(
                                            Res.string.give_this_a_room_of_its_own
                                        )
                                    )
                                }
                            }
                        }
                        item { HorizontalDivider() }

                        item {
                            Text(
                                text = stringResource(Res.string.details),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        item {
                            Card {
                                // The pair that is actually the dialect's
                                // identity, and the name that is not: nothing in
                                // the schema makes a dialect unique, so two rooms
                                // naming `st`-in-Lesotho differently hold the same
                                // dialect under two names.
                                DetailRow(
                                    label = stringResource(Res.string.language),
                                    value = dialect.language
                                )
                                DetailRow(
                                    label = stringResource(Res.string.country),
                                    value = dialect.country
                                )
                            }
                        }
                    }
                }
            }

            DialectDetailUIState.Loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MaterialTheme.spacing.containerPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(
                        MaterialTheme.spacing.containerPadding
                    )
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = stringResource(Res.string.dialect_detail),
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
        if (initialDialectDetailUIState == DialectDetailUIState.Loading) {
            dialectDetailViewModel.initiateDialectDetail()
        }
    }
}

@ConformancePreviews
@Composable
private fun DialectDetailScreenPreview() {
    MantraTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            DialectDetailScreen(
                activeUserPublicKey = "",
                dialectId = "dialectId",
                chatRoomId = "chatRoomId",
                relayHint = null,
                initialDialectDetailUIState = DialectDetailUIState.Loaded(
                    dialect = MantraDialect(
                        id = "dialectId",
                        publicKey = "author",
                        name = "Sesotho",
                        country = "Lesotho",
                        language = "st",
                        signature = "",
                        chatRoomId = "chatRoomId",
                    )
                ),
                mantraRepository = MantraRepository.NO_OP_MANTRA_REPOSITORY,
                chatRepository = ChatRepository.NO_OP_CHAT_REPOSITORY,
                onNavigateToRoute = {},
                onNavigateBack = {}
            )
        }
    }
}
