package press.mantra.compose.ui.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import press.mantra.compose.database.model.MantraChunk
import press.mantra.compose.repository.MantraRepository
import press.mantra.compose.ui.composable.navigation.routes.Route
import press.mantra.compose.ui.composable.navigation.routes.TranslateChunkRoute
import press.mantra.compose.ui.composable.widgets.LoadingDataIndicator
import press.mantra.compose.ui.theme.TorchTheme
import press.mantra.compose.ui.view.model.TranslationChapterViewModel
import press.mantra.compose.ui.view.state.ChunkTranslationPair
import press.mantra.compose.ui.view.state.TranslationChapterUIState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslationChapterScreen(
    activeUserPublicKey: HexKey,
    translationChapterId: String,
    chatRoomId: String,
    relayHint: String?,
    initialTranslationChapterUIState: TranslationChapterUIState = TranslationChapterUIState.Loading,
    mantraRepository: MantraRepository,
    onNavigateToRoute: (Route) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val translationChapterViewModel: TranslationChapterViewModel = viewModel(
        factory = TranslationChapterViewModel.factory(
            activeUserPublicKey = activeUserPublicKey,
            translationChapterId = translationChapterId,
            chatRoomId = chatRoomId,
            relayHint = relayHint,
            initialTranslationChapterUIState = initialTranslationChapterUIState,
            mantraRepository = mantraRepository,
        )
    )

    when (val translationChapterUIState = translationChapterViewModel.translationChapterUIState) {
        is TranslationChapterUIState.Error -> {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(50.dp))
                Text(text = translationChapterUIState.message)
            }
        }

        is TranslationChapterUIState.Loaded -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Chapter translation") },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        },
                    )
                }
            ) { innerPadding ->
                LazyColumn(
                    modifier = Modifier.padding(innerPadding).fillMaxSize()
                ) {
                    // Header row: dialect names.
                    item {
                        TableRow(
                            left = {
                                Text(
                                    text = translationChapterUIState.originalDialectName,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            right = {
                                Text(
                                    text = translationChapterUIState.translationDialectName,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                        )
                        HorizontalDivider()
                    }

                    if (translationChapterUIState.rows.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("This chapter has no chunks.")
                            }
                        }
                    } else {
                        items(
                            items = translationChapterUIState.rows,
                            key = { pair -> pair.originalChunk.id }
                        ) { pair ->
                            ChunkTranslationRow(
                                pair = pair,
                                onClick = {
                                    onNavigateToRoute.invoke(
                                        TranslateChunkRoute(
                                            activeUserPublicKey = activeUserPublicKey,
                                            translationChapterId = translationChapterId,
                                            chunkId = pair.originalChunk.id,
                                            chatRoomId = chatRoomId,
                                            relayHint = relayHint
                                        )
                                    )
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }

        TranslationChapterUIState.Loading -> {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Chapter Translation",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                LoadingDataIndicator(fillScreen = false)
                Spacer(modifier = Modifier.weight(2f))
            }
        }
    }

    LaunchedEffect(true) {
        if (initialTranslationChapterUIState == TranslationChapterUIState.Loading) {
            translationChapterViewModel.initiateTranslationChapter()
        }
    }
}

@Composable
private fun ChunkTranslationRow(
    pair: ChunkTranslationPair,
    onClick: () -> Unit,
) {
    val translated = pair.translationChunk?.text?.takeIf { it.isNotBlank() }
    TableRow(
        left = { Text(pair.originalChunk.text) },
        right = {
            // The translation cell is a button that opens the chunk translation
            // editor. When there is no translation yet, the original text is
            // shown greyed out as a placeholder.
            TextButton(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = translated ?: pair.originalChunk.text,
                    color = if (translated != null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    }
                )
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "Translate this chunk",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
    )
}

@Composable
private fun TableRow(
    left: @Composable () -> Unit,
    right: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
    ) {
        Box(modifier = Modifier.weight(1f).padding(12.dp)) { left() }
        VerticalDivider(modifier = Modifier.fillMaxHeight())
        Box(modifier = Modifier.weight(1f).padding(12.dp)) { right() }
    }
}

@Preview
@Composable
private fun TranslationChapterScreenPreview() {
    TorchTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            TranslationChapterScreen(
                activeUserPublicKey = "",
                translationChapterId = "translationChapterId",
                chatRoomId = "chatRoomId",
                relayHint = null,
                initialTranslationChapterUIState = TranslationChapterUIState.Loaded(
                    originalDialectName = "English",
                    translationDialectName = "Sesotho",
                    rows = listOf(
                        ChunkTranslationPair(
                            originalChunk = sampleChunk("c1", "When he was nearly thirteen, my brother Jem got his arm badly broken."),
                            translationChunk = null
                        ),
                        ChunkTranslationPair(
                            originalChunk = sampleChunk("c2", "When enough years had gone by to enable us to look back on them..."),
                            translationChunk = null
                        ),
                    )
                ),
                mantraRepository = MantraRepository.NO_OP_MANTRA_REPOSITORY,
                onNavigateToRoute = {},
                onNavigateBack = {}
            )
        }
    }
}

private fun sampleChunk(id: String, text: String) = MantraChunk(
    id = id,
    chapterId = "chapterId",
    publicKey = "author",
    text = text,
    index = 0,
    wordCount = 0,
    characterCount = 0,
    signature = "",
    chatRoomId = "chatRoomId"
)
