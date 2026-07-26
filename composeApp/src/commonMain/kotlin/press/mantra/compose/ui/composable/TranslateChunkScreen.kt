package press.mantra.compose.ui.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Card
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
import press.mantra.compose.database.model.MantraChunk
import press.mantra.compose.repository.MantraRepository
import press.mantra.compose.ui.composable.navigation.routes.Route
import press.mantra.compose.ui.composable.navigation.routes.TranslationChapterRoute
import press.mantra.compose.ui.composable.widgets.LoadingDataIndicator
import press.mantra.compose.ui.theme.TorchTheme
import press.mantra.compose.ui.view.model.TranslateChunkViewModel
import press.mantra.compose.ui.view.state.TranslateChunkUIState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslateChunkScreen(
    activeUserPublicKey: HexKey,
    translationChapterId: String,
    chunkId: String,
    chatRoomId: String,
    relayHint: String?,
    initialTranslateChunkUIState: TranslateChunkUIState = TranslateChunkUIState.Loading,
    mantraRepository: MantraRepository,
    onNavigateToRoute: (Route) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val translateChunkViewModel: TranslateChunkViewModel = viewModel(
        factory = TranslateChunkViewModel.factory(
            activeUserPublicKey = activeUserPublicKey,
            translationChapterId = translationChapterId,
            chunkId = chunkId,
            chatRoomId = chatRoomId,
            relayHint = relayHint,
            initialTranslateChunkUIState = initialTranslateChunkUIState,
            mantraRepository = mantraRepository,
        )
    )

    when (val translateChunkUIState = translateChunkViewModel.translateChunkUIState) {
        is TranslateChunkUIState.Error -> {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(50.dp))
                Text(text = translateChunkUIState.message)
            }
        }

        is TranslateChunkUIState.Loaded -> {
            val translationFieldState = rememberTextFieldState(translateChunkUIState.existingTranslationText)

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Translate chunk") },
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
                                    translateChunkViewModel.saveTranslation(
                                        translationField = translationFieldState,
                                        onSuccess = {
                                            // Return to a freshly-loaded chapter table so the
                                            // saved translation is reflected.
                                            onNavigateToRoute.invoke(
                                                TranslationChapterRoute(
                                                    activeUserPublicKey = activeUserPublicKey,
                                                    translationChapterId = translationChapterId,
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
                                    Icons.Default.Save,
                                    contentDescription = "Save translation"
                                )
                                Text("Save")
                            }
                        }
                    )
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Original",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Card {
                        Text(
                            modifier = Modifier.padding(16.dp),
                            text = translateChunkUIState.originalChunk.text,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Text(
                        text = "Translation",
                        style = MaterialTheme.typography.labelMedium
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        state = translationFieldState,
                        label = { Text("Translated text") },
                        placeholder = { Text("Enter the translation for this chunk") },
                    )
                }
            }
        }

        TranslateChunkUIState.Loading -> {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Translate Chunk",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                LoadingDataIndicator(fillScreen = false)
                Spacer(modifier = Modifier.weight(2f))
            }
        }
    }

    LaunchedEffect(true) {
        if (initialTranslateChunkUIState == TranslateChunkUIState.Loading) {
            translateChunkViewModel.initiateTranslateChunk()
        }
    }
}

@Preview
@Composable
private fun TranslateChunkScreenPreview() {
    TorchTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            TranslateChunkScreen(
                activeUserPublicKey = "",
                translationChapterId = "translationChapterId",
                chunkId = "chunkId",
                chatRoomId = "chatRoomId",
                relayHint = null,
                initialTranslateChunkUIState = TranslateChunkUIState.Loaded(
                    originalChunk = MantraChunk(
                        id = "chunkId",
                        chapterId = "chapterId",
                        publicKey = "author",
                        text = "When he was nearly thirteen, my brother Jem got his arm badly broken at the elbow.",
                        index = 0,
                        wordCount = 0,
                        characterCount = 0,
                        signature = "",
                        chatRoomId = "chatRoomId"
                    ),
                    existingTranslationText = ""
                ),
                mantraRepository = MantraRepository.NO_OP_MANTRA_REPOSITORY,
                onNavigateToRoute = {},
                onNavigateBack = {}
            )
        }
    }
}
