package press.mantra.compose.ui.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import press.mantra.compose.database.model.ChatRoom
import press.mantra.compose.database.model.MantraArtifact
import press.mantra.compose.database.model.MantraArtifactVersion
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.repository.ChatRepository
import press.mantra.compose.repository.FrostSigningRepository
import press.mantra.compose.repository.MantraRepository
import press.mantra.compose.files.rememberFilePicker
import press.mantra.compose.nostr.nip30303.ChapterProposal
import press.mantra.compose.text.ChapterNaming
import press.mantra.compose.text.ChapterText
import press.mantra.compose.text.Markdown
import press.mantra.compose.text.kibibytes
import press.mantra.compose.ui.composable.navigation.routes.FrostSigningRoute
import press.mantra.compose.ui.composable.navigation.routes.ImplementationPendingRoute
import press.mantra.compose.ui.composable.navigation.routes.Route
import press.mantra.compose.ui.composable.widgets.LoadingDataIndicator
import press.mantra.compose.ui.theme.MantraTheme
import press.mantra.compose.ui.view.model.AddChapterViewModel
import press.mantra.compose.ui.view.state.AddChapterUIState
import press.mantra.compose.ui.theme.spacing
import mantra.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import mantra.composeapp.generated.resources.add_chapter
import mantra.composeapp.generated.resources.chapter_name
import mantra.composeapp.generated.resources.eg_chapter_1_the_beginning
import mantra.composeapp.generated.resources.original_text_markdown
import mantra.composeapp.generated.resources.paste_the_chapter_s_markdown_blank_lines
import mantra.composeapp.generated.resources.propose_chapter
import mantra.composeapp.generated.resources.this_artifact_has_no_version_for_a_chapter
import mantra.composeapp.generated.resources.add_chapter_to
import mantra.composeapp.generated.resources.choose_chapter_files
import mantra.composeapp.generated.resources.d_chunks_is_more_than_the_group_can_sign
import mantra.composeapp.generated.resources.s_kib
import mantra.composeapp.generated.resources.s_of_s
import mantra.composeapp.generated.resources.this_chapter_is_too_long_to_propose
import mantra.composeapp.generated.resources.this_group_has_no_shared_key_for_a_chapter
import mantra.composeapp.generated.resources.words_characters_size_chunks
import mantra.composeapp.generated.resources.load_from_file
import mantra.composeapp.generated.resources.that_file_is_too_large
import mantra.composeapp.generated.resources.that_file_isn_t_text
import androidx.compose.material3.SnackbarHost
import press.mantra.compose.ui.composable.widgets.LocalSnackbarHostState
import press.mantra.compose.ui.composable.widgets.rememberNotifier
import press.mantra.compose.ui.theme.readableContent
import press.mantra.compose.ui.composable.widgets.ScreenStateTransition
import press.mantra.compose.ui.composable.widgets.NavigateBackButton
import press.mantra.compose.ui.composable.widgets.buttons.ExtendedFab
import press.mantra.compose.ui.theme.ConformancePreviews

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddChapterScreen(
    activeUserPublicKey: HexKey,
    artifactId: String,
    chatRoomId: String,
    relayHint: String?,
    initialAddChapterUIState: AddChapterUIState = AddChapterUIState.Loading,
    mantraRepository: MantraRepository,
    chatRepository: ChatRepository,
    frostSigningRepository: FrostSigningRepository,
    onNavigateToRouteAndPopUpInclusive: (Route) -> Unit,
    onNavigateToRoute: (Route) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val addChapterViewModel: AddChapterViewModel = viewModel(
        factory = AddChapterViewModel.factory(
            activeUserPublicKey = activeUserPublicKey,
            artifactId = artifactId,
            chatRoomId = chatRoomId,
            relayHint = relayHint,
            initialAddChapterUIState = initialAddChapterUIState,
            mantraRepository = mantraRepository,
            chatRepository = chatRepository,
            frostSigningRepository = frostSigningRepository,
        )
    )

    ScreenStateTransition(addChapterViewModel.addChapterUIState) { uiState ->
    when (val addChapterUIState = uiState) {
            is AddChapterUIState.Error -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(MaterialTheme.spacing.space600))
                    Text(text = addChapterUIState.message)
                }
            }

            is AddChapterUIState.Loaded -> {
                val nameFieldState = rememberTextFieldState()
                val originalTextFieldState = rememberTextFieldState()

                // Read above the handler, not inside it: `rememberNotifier` and
                // `stringResource` are composable and a click lambda is not.
                val notify = rememberNotifier(rememberCoroutineScope())
                val notText = stringResource(Res.string.that_file_isn_t_text)
                val tooLarge = stringResource(Res.string.that_file_is_too_large)

                // One file, into the form that already works. Picking several
                // chapters at once is the upload screen's job; here the point is
                // to stop a chapter having to be pasted through a keyboard.
                val picker = rememberFilePicker(
                    title = stringResource(Res.string.choose_chapter_files)
                ) { files ->
                    val file = files.firstOrNull() ?: return@rememberFilePicker

                    when (val decoded = ChapterText.decode(file.bytes)) {
                        is ChapterText.Result.Text -> {
                            originalTextFieldState.setTextAndPlaceCursorAtEnd(decoded.markdown)
                            // The filename only fills a name nobody has typed.
                            // Overwriting one that was typed would make loading a
                            // file a destructive act on a field beside it.
                            if (nameFieldState.text.isBlank()) {
                                nameFieldState.setTextAndPlaceCursorAtEnd(
                                    ChapterNaming.forFile(file.name, decoded.markdown)
                                )
                            }
                            // No snackbar on success. The text arriving in the
                            // field below is the feedback, and a message over
                            // the top of it would say less than the thing
                            // itself.
                        }

                        ChapterText.Result.NotText -> notify(notText)
                        ChapterText.Result.TooLarge -> notify(tooLarge)
                    }
                }

                // Live counts / paragraph (chunk) preview from the markdown text.
                val originalText = originalTextFieldState.text.toString()
                val wordCount = Markdown.wordCount(originalText)
                val characterCount = Markdown.characterCount(originalText)
                val chunks = Markdown.splitChunks(originalText)
                val chunkCount = chunks.size

                // A chapter hangs off a version, and the group has to be able to
                // sign; without both there is nothing this screen can propose.
                val artifactVersion = addChapterUIState.artifactVersion

                // The chapter and a chunk per paragraph are signed in one session,
                // and a session signs a bounded number of events. Past that the
                // chapter has to be split in two, which is worth saying while there
                // is still a cursor in the text rather than after a failed propose.
                val tooManyChunks = chunkCount > AddChapterViewModel.MAX_CHUNKS_PER_CHAPTER

                // The other cap, and the one that had been enforced nowhere: a
                // proposal carries the chapter whole and every chunk cut from
                // it, so it weighs a little over twice the prose plus an
                // envelope per event. Past what a group event holds, the session
                // used to go out and stall.
                val proposalBytes = ChapterProposal.proposalBytes(originalText, chunks)
                val tooLargeToPropose = !tooManyChunks && proposalBytes > ChapterProposal.MAX_PROPOSAL_BYTES

                val canProposeChapter = artifactVersion != null &&
                    addChapterUIState.canSign &&
                    chunkCount > 0 &&
                    !tooManyChunks &&
                    !tooLargeToPropose

                // imePadding: this screen has a text field, and without it the software
                // keyboard covers whatever is being typed into.
                Scaffold(
                    snackbarHost = { SnackbarHost(LocalSnackbarHostState.current) },
                    modifier = Modifier.imePadding(),
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(Res.string.add_chapter_to, addChapterUIState.artifact.name)) },
                            navigationIcon = { NavigateBackButton(onNavigateBack) },
                        )
                    },
                    bottomBar = {
                        BottomAppBar(
                            actions = {},
                            floatingActionButton = {
                                ExtendedFab(
                                    label = stringResource(Res.string.propose_chapter),
                                    icon = { Icon(Icons.Default.Add, contentDescription = "Propose chapter") },
                                    enabled = canProposeChapter,
                                    onClick = {
                                        if (artifactVersion == null) return@ExtendedFab

                                        addChapterViewModel.addChapter(
                                            localChatRoom = addChapterUIState.localChatRoom,
                                            artifactVersion = artifactVersion,
                                            nameField = nameFieldState,
                                            originalTextField = originalTextFieldState,
                                            onSuccess = { sessionId ->
                                                // Onto the session rather than back to
                                                // the artifact. Nothing has been created
                                                // yet -- the chapter appears when enough
                                                // members sign -- so landing on the list
                                                // it is not in would read as a failure.
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
                                                    ImplementationPendingRoute("Failed chapter")
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
                        modifier = Modifier.padding(innerPadding).readableContent().fillMaxSize().padding(MaterialTheme.spacing.space250),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space125)
                    ) {
                        if (!addChapterUIState.canSign) {
                            Text(
                                text = stringResource(Res.string.this_group_has_no_shared_key_for_a_chapter),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else if (artifactVersion == null) {
                            Text(
                                text = stringResource(Res.string.this_artifact_has_no_version_for_a_chapter),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else if (tooManyChunks) {
                            Text(
                                text = stringResource(
                                    Res.string.d_chunks_is_more_than_the_group_can_sign,
                                    chunkCount,
                                    AddChapterViewModel.MAX_CHUNKS_PER_CHAPTER,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else if (tooLargeToPropose) {
                            Text(
                                text = stringResource(
                                    Res.string.this_chapter_is_too_long_to_propose,
                                    chunkCount,
                                    stringResource(Res.string.s_kib, kibibytes(proposalBytes)),
                                    stringResource(
                                        Res.string.s_kib,
                                        kibibytes(ChapterProposal.MAX_PROPOSAL_BYTES),
                                    ),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            state = nameFieldState,
                            leadingIcon = {
                                Icon(
                                    Icons.Default.DriveFileRenameOutline,
                                    contentDescription = "Name of the chapter"
                                )
                            },
                            label = { Text(stringResource(Res.string.chapter_name)) },
                            placeholder = { Text(stringResource(Res.string.eg_chapter_1_the_beginning)) },
                        )

                        TextButton(onClick = { picker.launch(multiple = false) }) {
                            Icon(
                                Icons.Default.UploadFile,
                                contentDescription = "Load the chapter from a file"
                            )
                            Spacer(modifier = Modifier.width(MaterialTheme.spacing.space125))
                            Text(stringResource(Res.string.load_from_file))
                        }

                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            state = originalTextFieldState,
                            label = { Text(stringResource(Res.string.original_text_markdown)) },
                            placeholder = { Text(stringResource(Res.string.paste_the_chapter_s_markdown_blank_lines)) },
                        )

                        Text(
                            // Both caps, as the text is typed. The chunk count and
                            // the proposal's size are what the group is asked to
                            // carry, so they are a measure of the work being
                            // proposed rather than a curiosity about the text --
                            // and either of them can be the one that binds.
                            text = stringResource(
                                Res.string.words_characters_size_chunks,
                                wordCount,
                                characterCount,
                                stringResource(
                                    Res.string.s_of_s,
                                    stringResource(Res.string.s_kib, kibibytes(proposalBytes)),
                                    stringResource(
                                        Res.string.s_kib,
                                        kibibytes(ChapterProposal.MAX_PROPOSAL_BYTES),
                                    ),
                                ),
                                chunkCount,
                                AddChapterViewModel.MAX_CHUNKS_PER_CHAPTER,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (tooManyChunks || tooLargeToPropose) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.typography.labelMedium.color
                            }
                        )
                    }
                }
            }

            AddChapterUIState.Loading -> {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.space250),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space250)
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = stringResource(Res.string.add_chapter),
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
        if (initialAddChapterUIState == AddChapterUIState.Loading) {
            addChapterViewModel.initiateAddChapter()
        }
    }
}

@ConformancePreviews
@Composable
private fun AddChapterScreenPreview() {
    MantraTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            AddChapterScreen(
                activeUserPublicKey = "",
                artifactId = "artifactId",
                chatRoomId = "chatRoomId",
                relayHint = null,
                initialAddChapterUIState = AddChapterUIState.Loaded(
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
                ),
                mantraRepository = MantraRepository.NO_OP_MANTRA_REPOSITORY,
                chatRepository = ChatRepository.NO_OP_CHAT_REPOSITORY,
                frostSigningRepository = FrostSigningRepository.NO_OP_FROST_SIGNING_REPOSITORY,
                onNavigateToRouteAndPopUpInclusive = {},
                onNavigateToRoute = {},
                onNavigateBack = {}
            )
        }
    }
}
