package press.mantra.compose.ui.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import mantra.composeapp.generated.resources.Res
import mantra.composeapp.generated.resources.add_dialect
import mantra.composeapp.generated.resources.add_dialects_from_a_list
import mantra.composeapp.generated.resources.already_in_this_group
import mantra.composeapp.generated.resources.clear_the_selection
import mantra.composeapp.generated.resources.nothing_matched_that_search
import mantra.composeapp.generated.resources.pick_the_languages_this_group_will_translate_into
import mantra.composeapp.generated.resources.propose_dialect
import mantra.composeapp.generated.resources.propose_s_dialects
import mantra.composeapp.generated.resources.s_of_s_selected
import mantra.composeapp.generated.resources.search_by_language_country_or_code
import mantra.composeapp.generated.resources.select_everything_missing
import mantra.composeapp.generated.resources.this_group_already_has_every_language_in_the_list
import mantra.composeapp.generated.resources.this_group_has_no_shared_key_so_it_cannot_sign_a_dialect
import mantra.composeapp.generated.resources.waiting_for_the_group_to_sign_this_one
import org.jetbrains.compose.resources.stringResource
import press.mantra.compose.database.model.ChatRoom
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.language.AfricanLanguages
import press.mantra.compose.repository.ChatRepository
import press.mantra.compose.repository.FrostSigningRepository
import press.mantra.compose.repository.MantraRepository
import press.mantra.compose.ui.composable.navigation.routes.AddDialectRoute
import press.mantra.compose.ui.composable.navigation.routes.FrostSigningRoute
import press.mantra.compose.ui.composable.navigation.routes.Route
import press.mantra.compose.ui.composable.widgets.Decorative
import press.mantra.compose.ui.composable.widgets.EmptyState
import press.mantra.compose.ui.composable.widgets.ErrorState
import press.mantra.compose.ui.composable.widgets.LoadingDataIndicator
import press.mantra.compose.ui.composable.widgets.LocalSnackbarHostState
import press.mantra.compose.ui.composable.widgets.NavigateBackButton
import press.mantra.compose.ui.composable.widgets.ScreenStateTransition
import press.mantra.compose.ui.composable.widgets.buttons.ExtendedFab
import press.mantra.compose.ui.theme.ConformancePreviews
import press.mantra.compose.ui.theme.MantraTheme
import press.mantra.compose.ui.theme.readableContent
import press.mantra.compose.ui.theme.spacing
import press.mantra.compose.ui.view.model.AddDialectsFromCatalogueViewModel
import press.mantra.compose.ui.view.state.AddDialectsFromCatalogueUIState
import press.mantra.compose.ui.view.state.CatalogueRow
import press.mantra.compose.ui.view.state.matches

/**
 * Picking dialects out of the catalogue of African languages.
 *
 * Shows every entry and what this room can still ask for. A language the room
 * holds and a language it is already signing are both shown and neither can be
 * ticked, with different words on each, because "not available" has two
 * explanations and telling a member the wrong one is worse than telling them
 * nothing: one is theirs already, and the other is a proposal somebody else
 * opened that they can go and sign.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AddDialectsFromCatalogueScreen(
    activeUserPublicKey: HexKey,
    chatRoomId: String,
    relayHint: String?,
    initialUIState: AddDialectsFromCatalogueUIState = AddDialectsFromCatalogueUIState.Loading,
    chatRepository: ChatRepository,
    mantraRepository: MantraRepository,
    frostSigningRepository: FrostSigningRepository,
    onNavigateToRouteAndPopUpInclusive: (Route) -> Unit,
    onNavigateToRoute: (Route) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val viewModel: AddDialectsFromCatalogueViewModel = viewModel(
        factory = AddDialectsFromCatalogueViewModel.factory(
            activeUserPublicKey = activeUserPublicKey,
            chatRoomId = chatRoomId,
            relayHint = relayHint,
            initialUIState = initialUIState,
            chatRepository = chatRepository,
            mantraRepository = mantraRepository,
            frostSigningRepository = frostSigningRepository,
        )
    )

    val uiState by viewModel.uiState.collectAsState()

    ScreenStateTransition(uiState) { state ->
        when (state) {
            is AddDialectsFromCatalogueUIState.Error -> ErrorState(
                message = state.message,
                // A real retry: the state came from a database read that can
                // succeed on a second try, not from a navigation argument that
                // was already wrong.
                onRetry = viewModel::initiate,
            )

            is AddDialectsFromCatalogueUIState.Loaded -> {
                // imePadding: the search field is on this screen, and without it
                // the software keyboard covers what is being typed into.
                Scaffold(
                    snackbarHost = { SnackbarHost(LocalSnackbarHostState.current) },
                    modifier = Modifier.imePadding(),
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(Res.string.add_dialects_from_a_list)) },
                            navigationIcon = { NavigateBackButton(onNavigateBack) },
                        )
                    },
                    bottomBar = {
                        // Only where there is something to propose. A room that
                        // holds the whole catalogue gets the empty state and its
                        // way to the form, and "Propose 0 dialects" underneath it
                        // would be a button that can never do anything.
                        if (state.missing.isNotEmpty()) {
                            BottomAppBar(
                                actions = {},
                                floatingActionButton = {
                                    ProposeWaveFab(
                                        count = viewModel.selected.size,
                                        enabled = state.canSign &&
                                            viewModel.selected.isNotEmpty(),
                                        onClick = {
                                            viewModel.proposeSelectedDialects(
                                                localChatRoom = state.localChatRoom,
                                                onSuccess = { sessionId ->
                                                    // Onto the session rather than
                                                    // back to the group. Nothing
                                                    // has been created yet -- the
                                                    // dialects appear when enough
                                                    // members sign -- so landing on
                                                    // a list they are not in would
                                                    // read as a failure.
                                                    onNavigateToRouteAndPopUpInclusive.invoke(
                                                        FrostSigningRoute(
                                                            activeUserPublicKey =
                                                                activeUserPublicKey,
                                                            chatRoomId = chatRoomId,
                                                            sessionId = sessionId
                                                        )
                                                    )
                                                },
                                                onFailure = {},
                                            )
                                        }
                                    )
                                }
                            )
                        }
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier.padding(innerPadding).readableContent().fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space125),
                    ) {
                        Text(
                            stringResource(
                                Res.string.pick_the_languages_this_group_will_translate_into
                            )
                        )

                        if (!state.canSign) {
                            Text(
                                text = stringResource(
                                    Res.string
                                        .this_group_has_no_shared_key_so_it_cannot_sign_a_dialect
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }

                        OutlinedTextField(
                            value = viewModel.query.value,
                            onValueChange = viewModel::setQuery,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = Decorative)
                            },
                            label = {
                                Text(
                                    stringResource(Res.string.search_by_language_country_or_code)
                                )
                            },
                        )

                        when {
                            // Loaded is reached before the rows are read, so an
                            // empty list is "still reading" rather than "nothing".
                            state.rows.isEmpty() -> LoadingDataIndicator(fillScreen = false)

                            state.missing.isEmpty() -> EmptyState(
                                message = stringResource(
                                    Res.string
                                        .this_group_already_has_every_language_in_the_list
                                ),
                                icon = Icons.Default.Translate,
                                action = {
                                    TextButton(
                                        onClick = {
                                            onNavigateToRoute(
                                                AddDialectRoute(
                                                    activeUserPublicKey = activeUserPublicKey,
                                                    chatRoomId = chatRoomId,
                                                    relayHint = relayHint,
                                                )
                                            )
                                        }
                                    ) {
                                        Text(stringResource(Res.string.add_dialect))
                                    }
                                },
                            )

                            else -> {
                                SelectionSummary(
                                    selected = viewModel.selected.size,
                                    missing = state.missing.size,
                                    onSelectEverythingMissing = viewModel::selectEverythingMissing,
                                    onClearSelection = viewModel::clearSelection,
                                )

                                CatalogueList(state = state, viewModel = viewModel)
                            }
                        }
                    }
                }
            }

            AddDialectsFromCatalogueUIState.Loading -> LoadingDataIndicator(fillScreen = true)
        }
    }

    LaunchedEffect(true) {
        if (initialUIState == AddDialectsFromCatalogueUIState.Loading) {
            viewModel.initiate()
        }
    }
}

/**
 * Asks the group to sign what is ticked.
 *
 * Counted in the label because a wave is the thing being proposed and its size
 * is the whole of what distinguishes one from another -- a member who has
 * scrolled away from the summary still has to be told how many they are about to
 * put in front of the group.
 */
@Composable
private fun ProposeWaveFab(
    count: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ExtendedFab(
        label = if (count == 1) {
            stringResource(Res.string.propose_dialect)
        } else {
            stringResource(Res.string.propose_s_dialects, count)
        },
        icon = { Icon(Icons.Default.Add, contentDescription = Decorative) },
        enabled = enabled,
        onClick = onClick,
    )
}

/**
 * How much is ticked, and the two ways to change all of it at once.
 *
 * The count is of what is missing rather than of the catalogue, because the
 * catalogue's size is not a number a member can act on: "12 of 40 selected" says
 * how much is left to pick, and "12 of 56" would count rows they cannot tick.
 */
@Composable
private fun SelectionSummary(
    selected: Int,
    missing: Int,
    onSelectEverythingMissing: () -> Unit,
    onClearSelection: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space125),
    ) {
        Text(
            text = stringResource(Res.string.s_of_s_selected, selected, missing),
            style = MaterialTheme.typography.labelMedium,
        )

        if (selected == 0) {
            TextButton(onClick = onSelectEverythingMissing) {
                Text(stringResource(Res.string.select_everything_missing))
            }
        } else {
            TextButton(onClick = onClearSelection) {
                Text(stringResource(Res.string.clear_the_selection))
            }
        }
    }
}

/** The catalogue, filtered by the search and grouped under the country. */
@Composable
private fun CatalogueList(
    state: AddDialectsFromCatalogueUIState.Loaded,
    viewModel: AddDialectsFromCatalogueViewModel,
) {
    val query = viewModel.query.value
    val rows = state.rows.filter { it.language.matches(query) }

    if (rows.isEmpty()) {
        EmptyState(
            message = stringResource(Res.string.nothing_matched_that_search),
            icon = Icons.Default.Search,
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space50),
    ) {
        rows.forEachIndexed { index, row ->
            val language = row.language

            // The country, once, above the languages spoken there. A heading per
            // row would say "Nigeria" four times.
            if (rows.getOrNull(index - 1)?.language?.country != language.country) {
                item(key = "country ${language.country}") {
                    Text(
                        text = language.country,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = MaterialTheme.spacing.space125),
                    )
                }
            }

            item(key = "${language.code} ${language.country}") {
                LanguageRow(
                    row = row,
                    isSelected = viewModel.isSelected(language),
                    onToggle = { viewModel.toggle(language) },
                )
            }
        }
    }
}

/**
 * One catalogue entry.
 *
 * A clickable `Card` rather than a bare clickable modifier, so the whole row is
 * the target and is 48dp without anybody having to remember
 * `minimumInteractiveComponentSize` -- the same shape `SelectSubgroupAdminsScreen`
 * uses for picking members. (Spelled this way round because
 * `m3-touch-targets.py` reads comments as code; see the note in m3-audit.sh on
 * the colour check, which hit the same thing.)
 */
@Composable
private fun LanguageRow(
    row: CatalogueRow,
    isSelected: Boolean,
    onToggle: () -> Unit,
) {
    val missing = row.standing == CatalogueRow.Standing.MISSING

    Card(onClick = onToggle, enabled = missing) {
        ListItem(
            leadingContent = {
                // Decorative: the name is beside it and the checkbox carries the
                // state.
                Icon(Icons.Default.Translate, contentDescription = Decorative)
            },
            headlineContent = {
                Text(
                    text = row.language.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                // Which of the two reasons it cannot be ticked, since they are
                // different things to do about it. The code otherwise, which is
                // what a member searching by ISO 639 is looking for.
                Text(
                    text = when (row.standing) {
                        CatalogueRow.Standing.MISSING -> row.language.code
                        CatalogueRow.Standing.IN_THE_ROOM ->
                            stringResource(Res.string.already_in_this_group)

                        CatalogueRow.Standing.IN_FLIGHT ->
                            stringResource(Res.string.waiting_for_the_group_to_sign_this_one)
                    }
                )
            },
            trailingContent = {
                Checkbox(
                    checked = isSelected || row.standing == CatalogueRow.Standing.IN_THE_ROOM,
                    enabled = missing,
                    onCheckedChange = if (missing) {
                        { onToggle() }
                    } else {
                        null
                    },
                )
            }
        )
    }
}

private val previewRoom = LocalChatRoom(
    chatRoom = ChatRoom(
        id = "",
        userPublicKey = "",
        subject = "Message title",
        description = "See something. Say something",
        initialGiftWrapPayloadId = "sdfaer",
        mlsGroupState = null,
    ),
)

@ConformancePreviews
@Composable
private fun AddDialectsFromCataloguePreview() {
    MantraTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            AddDialectsFromCatalogueScreen(
                activeUserPublicKey = "",
                chatRoomId = "publicKey",
                relayHint = null,
                initialUIState = AddDialectsFromCatalogueUIState.Loaded(
                    localChatRoom = previewRoom,
                    canSign = true,
                    rows = CatalogueRow.rowsOf(
                        existing = emptyList(),
                        pending = listOf(AfricanLanguages.ALL.first()),
                    ),
                ),
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

@ConformancePreviews
@Composable
private fun AddDialectsFromCatalogueNothingLeftPreview() {
    MantraTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            AddDialectsFromCatalogueScreen(
                activeUserPublicKey = "",
                chatRoomId = "publicKey",
                relayHint = null,
                initialUIState = AddDialectsFromCatalogueUIState.Loaded(
                    localChatRoom = previewRoom,
                    canSign = true,
                    rows = CatalogueRow.rowsOf(
                        existing = emptyList(),
                        pending = AfricanLanguages.ALL,
                    ),
                ),
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
