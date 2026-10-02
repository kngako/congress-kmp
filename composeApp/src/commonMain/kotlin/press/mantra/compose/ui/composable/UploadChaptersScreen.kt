package press.mantra.compose.ui.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import mantra.composeapp.generated.resources.Res
import mantra.composeapp.generated.resources.add_more_files
import mantra.composeapp.generated.resources.at_most_d_files_in_one_upload
import mantra.composeapp.generated.resources.chapter_d_of_the_version
import mantra.composeapp.generated.resources.chapter_name
import mantra.composeapp.generated.resources.choose_chapter_files
import mantra.composeapp.generated.resources.choose_files
import mantra.composeapp.generated.resources.d_chapters_proposed
import mantra.composeapp.generated.resources.d_chunks_is_too_many_to_sign
import mantra.composeapp.generated.resources.d_proposed_d_failed
import mantra.composeapp.generated.resources.no_files_picked_yet
import mantra.composeapp.generated.resources.one_proposal_per_file
import mantra.composeapp.generated.resources.propose_d_chapters
import mantra.composeapp.generated.resources.propose_the_remaining_d
import mantra.composeapp.generated.resources.proposing_this_one_failed
import mantra.composeapp.generated.resources.remove_from_this_upload
import mantra.composeapp.generated.resources.s_is_more_than_a_proposal_carries
import mantra.composeapp.generated.resources.s_kib
import mantra.composeapp.generated.resources.same_text_as_s
import mantra.composeapp.generated.resources.this_artifact_has_no_version_for_a_chapter
import mantra.composeapp.generated.resources.this_file_has_no_name
import mantra.composeapp.generated.resources.this_file_is_empty
import mantra.composeapp.generated.resources.this_file_is_not_text
import mantra.composeapp.generated.resources.this_file_is_too_large
import mantra.composeapp.generated.resources.this_group_has_no_shared_key_for_a_chapter
import mantra.composeapp.generated.resources.upload_chapters_to
import mantra.composeapp.generated.resources.waiting_for_the_group_to_sign
import mantra.composeapp.generated.resources.words_characters
import org.jetbrains.compose.resources.stringResource
import press.mantra.compose.database.model.ChatRoom
import press.mantra.compose.database.model.MantraArtifact
import press.mantra.compose.database.model.MantraArtifactVersion
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.files.rememberFilePicker
import press.mantra.compose.nostr.nip30303.ChapterProposal
import press.mantra.compose.repository.ChatRepository
import press.mantra.compose.repository.FrostSigningRepository
import press.mantra.compose.repository.MantraRepository
import press.mantra.compose.text.kibibytes
import press.mantra.compose.ui.composable.navigation.routes.ProposalListRoute
import press.mantra.compose.ui.composable.navigation.routes.Route
import press.mantra.compose.ui.composable.widgets.LoadingDataIndicator
import press.mantra.compose.ui.composable.widgets.LocalSnackbarHostState
import press.mantra.compose.ui.composable.widgets.NavigateBackButton
import press.mantra.compose.ui.composable.widgets.ScreenStateTransition
import press.mantra.compose.ui.composable.widgets.EmptyState
import press.mantra.compose.ui.composable.widgets.ErrorState
import press.mantra.compose.ui.composable.widgets.buttons.ExtendedFab
import press.mantra.compose.ui.composable.widgets.rememberNotifier
import press.mantra.compose.ui.theme.ConformancePreviews
import press.mantra.compose.ui.theme.MantraTheme
import press.mantra.compose.ui.theme.readableContent
import press.mantra.compose.ui.theme.spacing
import press.mantra.compose.ui.view.model.UploadChaptersViewModel
import press.mantra.compose.ui.view.state.PickedChapter
import press.mantra.compose.ui.view.state.UploadChaptersUIState
import press.mantra.compose.ui.view.state.UploadProblem
import press.mantra.compose.ui.view.state.UploadReview

/**
 * Picking files, reviewing what they would become, and putting each to the
 * group.
 *
 * **A review step, not a submit button**, and that is where this departs
 * visibly from the web app it mirrors. mantra.press can afford to post twelve
 * files and redirect, because what it creates belongs to the uploader and the
 * uploader can fix it. Here each file becomes a request for the group's own
 * signature, so a filename standing in for a title, a file that decoded to
 * nothing, or a chapter too long to carry is wrong in something a quorum is
 * about to put its name to. Picking files therefore produces a list, and the
 * list is what is proposed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadChaptersScreen(
    activeUserPublicKey: HexKey,
    artifactId: String,
    chatRoomId: String,
    relayHint: String?,
    initialUIState: UploadChaptersUIState = UploadChaptersUIState.Loading,
    mantraRepository: MantraRepository,
    chatRepository: ChatRepository,
    frostSigningRepository: FrostSigningRepository,
    onNavigateToRouteAndPopUpInclusive: (Route) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val viewModel: UploadChaptersViewModel = viewModel(
        factory = UploadChaptersViewModel.factory(
            activeUserPublicKey = activeUserPublicKey,
            artifactId = artifactId,
            chatRoomId = chatRoomId,
            relayHint = relayHint,
            initialUIState = initialUIState,
            mantraRepository = mantraRepository,
            chatRepository = chatRepository,
            frostSigningRepository = frostSigningRepository,
        )
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ScreenStateTransition(uiState) { state ->
        when (state) {
            UploadChaptersUIState.Loading -> LoadingDataIndicator(fillScreen = true)

            is UploadChaptersUIState.Error ->
                ErrorState(
                    message = state.message,
                    onRetry = { viewModel.initiate() },
                )

            is UploadChaptersUIState.Loaded -> {
                val notify = rememberNotifier(rememberCoroutineScope())

                // The sentence is built in composition and shown from an effect,
                // because `stringResource` is composable and the propose
                // callback is not. Formatting it by hand in the callback was the
                // first draft and meant doing a resource's own job with
                // `replaceFirst`.
                val outcomeMessage = state.lastOutcome?.let { outcome ->
                    if (outcome.failed > 0) {
                        stringResource(
                            Res.string.d_proposed_d_failed,
                            outcome.proposed,
                            outcome.failed,
                        )
                    } else {
                        stringResource(Res.string.d_chapters_proposed, outcome.proposed)
                    }
                }

                LaunchedEffect(state.lastOutcome?.pass) {
                    outcomeMessage?.let(notify)
                }

                val picker = rememberFilePicker(
                    title = stringResource(Res.string.choose_chapter_files)
                ) { files -> viewModel.addFiles(files) }

                val version = state.artifactVersion

                // Where each row lands, counted over the rows not yet proposed:
                // a proposed chapter has taken its number and the next one does
                // not take it again.
                val offsets = remember(state.chapters) {
                    var next = 0
                    buildMap {
                        state.chapters.forEach { chapter ->
                            if (chapter.sessionId == null) put(chapter.id, next++)
                        }
                    }
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(LocalSnackbarHostState.current) },
                    // A name field per row, so the keyboard must not cover the
                    // row being named.
                    modifier = Modifier.imePadding(),
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    stringResource(
                                        Res.string.upload_chapters_to,
                                        state.artifact.name,
                                    )
                                )
                            },
                            navigationIcon = { NavigateBackButton(onNavigateBack) },
                        )
                    },
                    bottomBar = {
                        BottomAppBar(
                            actions = {},
                            floatingActionButton = {
                                val remaining = state.proposable.size
                                val isRetry = state.proposed.isNotEmpty()

                                ExtendedFab(
                                    label = if (isRetry) {
                                        stringResource(Res.string.propose_the_remaining_d, remaining)
                                    } else {
                                        stringResource(Res.string.propose_d_chapters, remaining)
                                    },
                                    icon = {
                                        Icon(
                                            Icons.Default.UploadFile,
                                            contentDescription = "Propose these chapters"
                                        )
                                    },
                                    enabled = state.canPropose,
                                    onClick = {
                                        if (version == null) return@ExtendedFab

                                        viewModel.propose(
                                            localChatRoom = state.localChatRoom,
                                            artifactVersion = version,
                                        ) { proposed, failed ->
                                            // Leaves only when there is nothing
                                            // left to aim a retry from. On a
                                            // partial pass the screen stays: the
                                            // rows that went are marked and the
                                            // rest keep their reason, and that
                                            // list is the only place a retry can
                                            // be aimed from.
                                            if (failed == 0 && proposed > 0) {
                                                // The proposal list, not a
                                                // signing screen: at N there is
                                                // no one session to watch, and
                                                // the list is the screen that
                                                // exists for showing several.
                                                onNavigateToRouteAndPopUpInclusive(
                                                    ProposalListRoute(
                                                        activeUserPublicKey = activeUserPublicKey,
                                                        chatRoomId = chatRoomId,
                                                    )
                                                )
                                            }
                                        }
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
                            .padding(MaterialTheme.spacing.containerPadding),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.relatedGap)
                    ) {
                        item {
                            // Said before the picker opens, not after. An upload
                            // spends the room's attention, and the person
                            // picking files is the only one who can weigh that.
                            Text(
                                text = stringResource(Res.string.one_proposal_per_file),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (!state.canSign) {
                            item {
                                Text(
                                    text = stringResource(
                                        Res.string.this_group_has_no_shared_key_for_a_chapter
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        } else if (version == null) {
                            item {
                                Text(
                                    text = stringResource(
                                        Res.string.this_artifact_has_no_version_for_a_chapter
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(
                                    MaterialTheme.spacing.itemGap
                                ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    enabled = state.chapters.size <
                                        UploadChaptersViewModel.MAX_FILES_PER_UPLOAD,
                                    onClick = { picker.launch(multiple = true) }
                                ) {
                                    Icon(
                                        Icons.Default.UploadFile,
                                        contentDescription = "Choose files to upload"
                                    )
                                    Text(
                                        modifier = Modifier.padding(
                                            start = MaterialTheme.spacing.itemGap
                                        ),
                                        text = if (state.chapters.isEmpty()) {
                                            stringResource(Res.string.choose_files)
                                        } else {
                                            stringResource(Res.string.add_more_files)
                                        }
                                    )
                                }

                                Text(
                                    text = stringResource(
                                        Res.string.at_most_d_files_in_one_upload,
                                        UploadChaptersViewModel.MAX_FILES_PER_UPLOAD,
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (state.chapters.isEmpty()) {
                            item {
                                EmptyState(
                                    message = stringResource(Res.string.no_files_picked_yet),
                                    icon = Icons.Default.UploadFile,
                                )
                            }
                        }

                        items(items = state.chapters, key = { it.id }) { chapter ->
                            PickedChapterCard(
                                chapter = chapter,
                                index = state.baseIndex + (offsets[chapter.id] ?: 0),
                                onRename = { viewModel.rename(chapter.id, it) },
                                onRemove = { viewModel.remove(chapter.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(true) {
        if (initialUIState == UploadChaptersUIState.Loading) viewModel.initiate()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickedChapterCard(
    chapter: PickedChapter,
    index: Int,
    onRename: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val proposed = chapter.sessionId != null

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.containerPadding),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.compactPadding)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = chapter.fileName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (proposed) {
                            stringResource(Res.string.waiting_for_the_group_to_sign)
                        } else {
                            stringResource(Res.string.chapter_d_of_the_version, index)
                        },
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                if (proposed) {
                    Icon(
                        Icons.Default.HourglassTop,
                        contentDescription = "Waiting for the group to sign"
                    )
                } else {
                    IconButton(onClick = onRemove) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(
                                Res.string.remove_from_this_upload
                            )
                        )
                    }
                }
            }

            if (!proposed) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = chapter.name,
                    onValueChange = onRename,
                    isError = chapter.problem == UploadProblem.Unnamed,
                    label = { Text(stringResource(Res.string.chapter_name)) },
                    singleLine = true,
                )
            }

            Text(
                text = stringResource(
                    Res.string.words_characters,
                    chapter.wordCount,
                    chapter.characterCount,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            chapter.problem?.let { problem ->
                Text(
                    text = problem.describe(),
                    style = MaterialTheme.typography.bodySmall,
                    // A warning is not an error. A duplicate is a thing worth
                    // saying and not a thing that stops the upload, and giving
                    // the two one colour would make the second look like the
                    // first.
                    color = if (problem.blocking) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
private fun UploadProblem.describe(): String = when (this) {
    UploadProblem.NotText -> stringResource(Res.string.this_file_is_not_text)
    UploadProblem.TooLarge -> stringResource(Res.string.this_file_is_too_large)
    UploadProblem.Empty -> stringResource(Res.string.this_file_is_empty)
    UploadProblem.Unnamed -> stringResource(Res.string.this_file_has_no_name)
    UploadProblem.ProposalFailed -> stringResource(Res.string.proposing_this_one_failed)
    is UploadProblem.TooManyChunks -> stringResource(
        Res.string.d_chunks_is_too_many_to_sign,
        count,
        UploadReview.MAX_CHUNKS_PER_CHAPTER,
    )
    is UploadProblem.TooLargeToPropose -> stringResource(
        Res.string.s_is_more_than_a_proposal_carries,
        stringResource(Res.string.s_kib, kibibytes(bytes)),
        stringResource(Res.string.s_kib, kibibytes(ChapterProposal.MAX_PROPOSAL_BYTES)),
    )
    is UploadProblem.Duplicate -> stringResource(Res.string.same_text_as_s, of)
}

@ConformancePreviews
@Composable
private fun UploadChaptersScreenPreview() {
    MantraTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            UploadChaptersScreen(
                activeUserPublicKey = "",
                artifactId = "artifactId",
                chatRoomId = "chatRoomId",
                relayHint = null,
                initialUIState = UploadChaptersUIState.Loaded(
                    localChatRoom = LocalChatRoom(
                        chatRoom = ChatRoom(
                            id = "chatRoomId",
                            subject = "Message title",
                            description = "See something. Say somethin"
                        ),
                    ),
                    artifact = MantraArtifact(
                        id = "artifactId",
                        publicKey = "author",
                        name = "To Kill a Mockingbird",
                        url = "https://harper.com/2-kill-Bird",
                        visibility = "private",
                        dialectId = "dialectId",
                        license = "cc",
                        chatRoomId = "chatRoomId",
                        signature = ""
                    ),
                    artifactVersion = MantraArtifactVersion(
                        id = "artifactVersionId",
                        artifactId = "artifactId",
                        publicKey = "author",
                        versionLabel = "1.0",
                        chatRoomId = "chatRoomId",
                        signature = ""
                    ),
                    canSign = true,
                    baseIndex = 3,
                    chapters = listOf(
                        PickedChapter(
                            id = "0:01-the-wreck.md",
                            fileName = "01-the-wreck.md",
                            name = "The wreck",
                            markdown = "One.\n\nTwo.",
                            chunks = listOf("One.", "Two."),
                            wordCount = 2,
                            characterCount = 10,
                            proposalBytes = 1400,
                        ),
                        PickedChapter(
                            id = "1:02-cover.png",
                            fileName = "02-cover.png",
                            name = "",
                            markdown = "",
                            chunks = emptyList(),
                            wordCount = 0,
                            characterCount = 0,
                            proposalBytes = 448,
                            problem = UploadProblem.NotText,
                        ),
                    ),
                ),
                mantraRepository = MantraRepository.NO_OP_MANTRA_REPOSITORY,
                chatRepository = ChatRepository.NO_OP_CHAT_REPOSITORY,
                frostSigningRepository = FrostSigningRepository.NO_OP_FROST_SIGNING_REPOSITORY,
                onNavigateToRouteAndPopUpInclusive = {},
                onNavigateBack = {}
            )
        }
    }
}
