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
import androidx.compose.material.icons.filled.Article
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
import press.mantra.compose.database.model.MantraTranslationArtifactVersion
import press.mantra.compose.repository.MantraRepository
import press.mantra.compose.ui.composable.navigation.routes.Route
import press.mantra.compose.ui.composable.navigation.routes.TranslationChapterRoute
import press.mantra.compose.ui.composable.widgets.DetailRow
import press.mantra.compose.ui.composable.widgets.LoadingDataIndicator
import press.mantra.compose.ui.theme.TorchTheme
import press.mantra.compose.ui.view.model.TranslationArtifactVersionDetailViewModel
import press.mantra.compose.ui.view.state.TranslationArtifactVersionDetailUIState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslationArtifactVersionDetailScreen(
    activeUserPublicKey: HexKey,
    translationArtifactVersionId: String,
    chatRoomId: String,
    relayHint: String?,
    initialTranslationArtifactVersionDetailUIState: TranslationArtifactVersionDetailUIState = TranslationArtifactVersionDetailUIState.Loading,
    mantraRepository: MantraRepository,
    onNavigateToRoute: (Route) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val translationDetailViewModel: TranslationArtifactVersionDetailViewModel = viewModel(
        factory = TranslationArtifactVersionDetailViewModel.factory(
            activeUserPublicKey = activeUserPublicKey,
            translationArtifactVersionId = translationArtifactVersionId,
            chatRoomId = chatRoomId,
            relayHint = relayHint,
            initialTranslationArtifactVersionDetailUIState = initialTranslationArtifactVersionDetailUIState,
            mantraRepository = mantraRepository,
        )
    )

    when (val translationDetailUIState = translationDetailViewModel.translationDetailUIState) {
        is TranslationArtifactVersionDetailUIState.Error -> {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(50.dp))
                Text(text = translationDetailUIState.message)
            }
        }

        is TranslationArtifactVersionDetailUIState.Loaded -> {
            val translation = translationDetailUIState.translation
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(translation.name) },
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
                    // Details
                    item {
                        Text(
                            text = "Details",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    item {
                        Card {
                            DetailRow(label = "Name", value = translation.name)
                            DetailRow(label = "Dialect", value = translation.dialectId)
                            DetailRow(label = "Visibility", value = translation.visibility)
                            DetailRow(label = "License", value = translation.license)
                            DetailRow(label = "Source version", value = translation.artifactVersionId)
                            DetailRow(label = "Author", value = translation.publicKey)
                            DetailRow(label = "Created", value = translation.createdAt.toString())
                        }
                    }

                    item { HorizontalDivider() }

                    // Chapters with translation progress
                    item {
                        Text(
                            text = "Chapters",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    if (translationDetailUIState.chapters.isEmpty()) {
                        item { Text("No chapters.") }
                    } else {
                        items(
                            items = translationDetailUIState.chapters,
                            key = { progress -> progress.chapter.id }
                        ) { progress ->
                            Card(
                                onClick = {
                                    onNavigateToRoute.invoke(
                                        TranslationChapterRoute(
                                            activeUserPublicKey = activeUserPublicKey,
                                            translationChapterId = progress.chapter.id,
                                            chatRoomId = chatRoomId,
                                            relayHint = relayHint
                                        )
                                    )
                                }
                            ) {
                                ListItem(
                                    leadingContent = {
                                        Icon(
                                            Icons.Default.Article,
                                            contentDescription = "Chapter"
                                        )
                                    },
                                    headlineContent = { Text("Chapter ${progress.chapter.index}") },
                                    supportingContent = {
                                        Text("${progress.translatedChunks}/${progress.totalChunks} chunks translated")
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        TranslationArtifactVersionDetailUIState.Loading -> {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Translation Detail",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                LoadingDataIndicator(fillScreen = false)
                Spacer(modifier = Modifier.weight(2f))
            }
        }
    }

    LaunchedEffect(true) {
        if (initialTranslationArtifactVersionDetailUIState == TranslationArtifactVersionDetailUIState.Loading) {
            translationDetailViewModel.initiateTranslationArtifactVersionDetail()
        }
    }
}

@Preview
@Composable
private fun TranslationArtifactVersionDetailScreenPreview() {
    TorchTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            TranslationArtifactVersionDetailScreen(
                activeUserPublicKey = "",
                translationArtifactVersionId = "translationId",
                chatRoomId = "chatRoomId",
                relayHint = null,
                initialTranslationArtifactVersionDetailUIState = TranslationArtifactVersionDetailUIState.Loaded(
                    translation = MantraTranslationArtifactVersion(
                        id = "translationId",
                        publicKey = "author",
                        artifactVersionId = "versionId",
                        dialectId = "dialectId",
                        name = "Sesotho",
                        visibility = "private",
                        license = "cc",
                        signature = "",
                        chatRoomId = "chatRoomId"
                    )
                ),
                mantraRepository = MantraRepository.NO_OP_MANTRA_REPOSITORY,
                onNavigateToRoute = {},
                onNavigateBack = {}
            )
        }
    }
}
