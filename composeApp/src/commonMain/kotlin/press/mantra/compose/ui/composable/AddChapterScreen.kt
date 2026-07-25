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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import press.mantra.compose.database.model.MantraArtifact
import press.mantra.compose.repository.MantraRepository
import press.mantra.compose.text.Markdown
import press.mantra.compose.ui.composable.navigation.routes.ArtifactDetailRoute
import press.mantra.compose.ui.composable.navigation.routes.Route
import press.mantra.compose.ui.composable.widgets.LoadingDataIndicator
import press.mantra.compose.ui.theme.TorchTheme
import press.mantra.compose.ui.view.model.AddChapterViewModel
import press.mantra.compose.ui.view.state.AddChapterUIState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddChapterScreen(
    activeUserPublicKey: HexKey,
    artifactId: String,
    chatRoomId: String,
    relayHint: String?,
    initialAddChapterUIState: AddChapterUIState = AddChapterUIState.Loading,
    mantraRepository: MantraRepository,
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
        )
    )

    when (val addChapterUIState = addChapterViewModel.addChapterUIState) {
        is AddChapterUIState.Error -> {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(50.dp))
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
                                onClick = {
                                    addChapterViewModel.addChapter(
                                        nameField = nameFieldState,
                                        originalTextField = originalTextFieldState,
                                        onSuccess = {
                                            onNavigateToRoute.invoke(
                                                ArtifactDetailRoute(
                                                    activeUserPublicKey = activeUserPublicKey,
                                                    artifactId = artifactId,
                                                    chatRoomId = chatRoomId,
                                                    relayHint = relayHint
                                                )
                                            )
                                        },
                                        onFailure = {}
                                    )
                                }
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Add chapter"
                                )
                                Text("Add Chapter")
                            }
                        }
                    )
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier.padding(innerPadding).fillMaxSize().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
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
                        text = "$wordCount words · $characterCount characters · $paragraphCount ${if (paragraphCount == 1) "chunk" else "chunks"}",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        AddChapterUIState.Loading -> {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
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
                    )
                ),
                mantraRepository = MantraRepository.NO_OP_MANTRA_REPOSITORY,
                onNavigateToRoute = {},
                onNavigateBack = {}
            )
        }
    }
}
