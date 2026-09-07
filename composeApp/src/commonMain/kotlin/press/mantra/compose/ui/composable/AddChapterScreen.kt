package press.mantra.compose.ui.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
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
import press.mantra.compose.text.Markdown
import press.mantra.compose.ui.composable.navigation.routes.FrostSigningRoute
import press.mantra.compose.ui.composable.navigation.routes.ImplementationPendingRoute
import press.mantra.compose.ui.composable.navigation.routes.Route
import press.mantra.compose.ui.composable.widgets.LoadingDataIndicator
import press.mantra.compose.ui.theme.TorchTheme
import press.mantra.compose.ui.view.model.AddChapterViewModel
import press.mantra.compose.ui.view.state.AddChapterUIState
import press.mantra.compose.ui.theme.spacing

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

    when (val addChapterUIState = addChapterViewModel.addChapterUIState) {
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

            // Live counts / paragraph (chunk) preview from the markdown text.
            val originalText = originalTextFieldState.text.toString()
            val wordCount = Markdown.wordCount(originalText)
            val characterCount = Markdown.characterCount(originalText)
            val paragraphCount = Markdown.splitParagraphs(originalText).size

            // A chapter hangs off a version, and the group has to be able to
            // sign; without both there is nothing this screen can propose.
            val artifactVersion = addChapterUIState.artifactVersion

            // The chapter and a chunk per paragraph are signed in one session,
            // and a session signs a bounded number of events. Past that the
            // chapter has to be split in two, which is worth saying while there
            // is still a cursor in the text rather than after a failed propose.
            val tooManyChunks = paragraphCount > AddChapterViewModel.MAX_CHUNKS_PER_CHAPTER

            val canProposeChapter = artifactVersion != null &&
                addChapterUIState.canSign &&
                paragraphCount > 0 &&
                !tooManyChunks

            // M3 gives a FAB no `enabled`, so borrow the disabled colours every
            // other button in the app uses rather than inventing a shade here.
            val buttonColors = ButtonDefaults.buttonColors()

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Add chapter to ${addChapterUIState.artifact.name}") },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        },
                    )
                },
                bottomBar = {
                    BottomAppBar(
                        actions = {},
                        floatingActionButton = {
                            ExtendedFloatingActionButton(
                                modifier = if (canProposeChapter) {
                                    Modifier
                                } else {
                                    // Looking unavailable is not being unavailable.
                                    Modifier.semantics { disabled() }
                                },
                                containerColor = if (canProposeChapter) {
                                    FloatingActionButtonDefaults.containerColor
                                } else {
                                    buttonColors.disabledContainerColor
                                },
                                contentColor = if (canProposeChapter) {
                                    contentColorFor(FloatingActionButtonDefaults.containerColor)
                                } else {
                                    buttonColors.disabledContentColor
                                },
                                onClick = {
                                    if (artifactVersion == null || !canProposeChapter) {
                                        return@ExtendedFloatingActionButton
                                    }

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
                                                ImplementationPendingRoute("Failed Chapter")
                                            )
                                        }
                                    )
                                }
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Propose chapter"
                                )
                                Text("Propose Chapter")
                            }
                        }
                    )
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier.padding(innerPadding).fillMaxSize().padding(MaterialTheme.spacing.space250),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space125)
                ) {
                    if (!addChapterUIState.canSign) {
                        Text(
                            text = "This group has no shared key, so it cannot sign a " +
                                "chapter into existence. Run a shared key ceremony first.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else if (artifactVersion == null) {
                        Text(
                            text = "This artifact has no version for a chapter to attach to.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else if (tooManyChunks) {
                        Text(
                            text = "$paragraphCount chunks is more than the group can sign in " +
                                "one go. Split the chapter so that no part of it is over " +
                                "${AddChapterViewModel.MAX_CHUNKS_PER_CHAPTER} paragraphs.",
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
                        label = { Text("Chapter Name") },
                        placeholder = { Text("eg. Chapter 1 — The Beginning") },
                    )

                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        state = originalTextFieldState,
                        label = { Text("Original text (markdown)") },
                        placeholder = { Text("Paste the chapter's markdown. Blank lines separate paragraphs into chunks.") },
                    )

                    Text(
                        // The chunk count is what the group is asked to sign
                        // alongside the chapter, so it is a count of the work
                        // being proposed rather than a curiosity about the text.
                        text = "$wordCount words · $characterCount characters · " +
                            "$paragraphCount ${if (paragraphCount == 1) "chunk" else "chunks"} " +
                            "of ${AddChapterViewModel.MAX_CHUNKS_PER_CHAPTER}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (tooManyChunks) {
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
                    text = "Add Chapter",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                LoadingDataIndicator(fillScreen = false)
                Spacer(modifier = Modifier.weight(2f))
            }
        }
    }

    LaunchedEffect(true) {
        if (initialAddChapterUIState == AddChapterUIState.Loading) {
            addChapterViewModel.initiateAddChapter()
        }
    }
}

@Preview
@Composable
private fun AddChapterScreenPreview() {
    TorchTheme {
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
                            userPublicKey = "",
                            subject = "Message title",
                            description = "See something. Say somethin",
                            initialGiftWrapPayloadId = "sdfaer",
                            mlsGroupState = null
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
