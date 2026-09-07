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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Segment
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import press.mantra.compose.database.model.MantraChapter
import press.mantra.compose.repository.MantraRepository
import press.mantra.compose.ui.composable.widgets.LoadingDataIndicator
import press.mantra.compose.ui.theme.TorchTheme
import press.mantra.compose.ui.view.model.ChapterDetailViewModel
import press.mantra.compose.ui.view.state.ChapterDetailUIState
import press.mantra.compose.ui.theme.spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterDetailScreen(
    activeUserPublicKey: HexKey,
    chapterId: String,
    chatRoomId: String,
    relayHint: String?,
    initialChapterDetailUIState: ChapterDetailUIState = ChapterDetailUIState.Loading,
    mantraRepository: MantraRepository,
    onNavigateBack: () -> Unit,
) {
    val chapterDetailViewModel: ChapterDetailViewModel = viewModel(
        factory = ChapterDetailViewModel.factory(
            activeUserPublicKey = activeUserPublicKey,
            chapterId = chapterId,
            chatRoomId = chatRoomId,
            relayHint = relayHint,
            initialChapterDetailUIState = initialChapterDetailUIState,
            mantraRepository = mantraRepository,
        )
    )

    when (val chapterDetailUIState = chapterDetailViewModel.chapterDetailUIState) {
        is ChapterDetailUIState.Error -> {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(MaterialTheme.spacing.space600))
                Text(text = chapterDetailUIState.message)
            }
        }

        is ChapterDetailUIState.Loaded -> {
            val chapter = chapterDetailUIState.chapter
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(chapter.name) },
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
                    modifier = Modifier.padding(innerPadding).fillMaxSize().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Text(
                            text = "Chapter ${chapter.index} · ${chapter.wordCount} words · ${chapter.characterCount} characters",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    // Original text (markdown, shown as-is).
                    item {
                        Text(
                            text = "Original text",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    item {
                        Card {
                            Text(
                                modifier = Modifier.padding(16.dp),
                                text = chapter.originalText,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    item { HorizontalDivider() }

                    // Chunks
                    item {
                        Text(
                            text = "Chunks (${chapterDetailUIState.chunks.size})",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    if (chapterDetailUIState.chunks.isEmpty()) {
                        item { Text("No chunks.") }
                    } else {
                        items(
                            items = chapterDetailUIState.chunks,
                            key = { chunk -> chunk.id }
                        ) { chunk ->
                            Card {
                                ListItem(
                                    leadingContent = {
                                        Icon(
                                            Icons.Default.Segment,
                                            contentDescription = "Chunk"
                                        )
                                    },
                                    overlineContent = { Text("Chunk ${chunk.index}") },
                                    headlineContent = { Text(chunk.text) },
                                    supportingContent = {
                                        Text("${chunk.wordCount} words · ${chunk.characterCount} characters")
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        ChapterDetailUIState.Loading -> {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Chapter Detail",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                LoadingDataIndicator(fillScreen = false)
                Spacer(modifier = Modifier.weight(2f))
            }
        }
    }

    LaunchedEffect(true) {
        if (initialChapterDetailUIState == ChapterDetailUIState.Loading) {
            chapterDetailViewModel.initiateChapterDetail()
        }
    }
}

@Preview
@Composable
private fun ChapterDetailScreenPreview() {
    TorchTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            ChapterDetailScreen(
                activeUserPublicKey = "",
                chapterId = "chapterId",
                chatRoomId = "chatRoomId",
                relayHint = null,
                initialChapterDetailUIState = ChapterDetailUIState.Loaded(
                    chapter = MantraChapter(
                        id = "chapterId",
                        artifactVersionId = "versionId",
                        publicKey = "author",
                        name = "Chapter 1 — The Beginning",
                        originalText = "When he was nearly thirteen...\n\nWhen enough years had gone by...",
                        index = 0,
                        wordCount = 12,
                        characterCount = 64,
                        signature = "",
                        chatRoomId = "chatRoomId"
                    )
                ),
                mantraRepository = MantraRepository.NO_OP_MANTRA_REPOSITORY,
                onNavigateBack = {}
            )
        }
    }
}
