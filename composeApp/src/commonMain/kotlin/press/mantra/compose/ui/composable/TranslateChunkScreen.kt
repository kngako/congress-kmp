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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
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
import press.mantra.compose.database.model.MantraChunk
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.repository.ChatRepository
import press.mantra.compose.repository.FrostSigningRepository
import press.mantra.compose.repository.MantraRepository
import press.mantra.compose.ui.composable.navigation.routes.FrostSigningRoute
import press.mantra.compose.ui.composable.navigation.routes.ImplementationPendingRoute
import press.mantra.compose.ui.composable.navigation.routes.Route
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
    chatRepository: ChatRepository,
    frostSigningRepository: FrostSigningRepository,
    onNavigateToRouteAndPopUpInclusive: (Route) -> Unit,
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
            chatRepository = chatRepository,
            frostSigningRepository = frostSigningRepository,
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

            // M3 gives a FAB no `enabled`, so borrow the disabled colours every
            // other button in the app uses rather than inventing a shade here.
            val buttonColors = ButtonDefaults.buttonColors()

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
                                modifier = if (translateChunkUIState.canSign) {
                                    Modifier
                                } else {
                                    // Looking unavailable is not being unavailable:
                                    // without this a screen reader still announces
                                    // a button it is happy to press.
                                    Modifier.semantics { disabled() }
                                },
                                containerColor = if (translateChunkUIState.canSign) {
                                    FloatingActionButtonDefaults.containerColor
                                } else {
                                    buttonColors.disabledContainerColor
                                },
                                contentColor = if (translateChunkUIState.canSign) {
                                    contentColorFor(FloatingActionButtonDefaults.containerColor)
                                } else {
                                    buttonColors.disabledContentColor
                                },
                                onClick = {
                                    if (!translateChunkUIState.canSign) return@ExtendedFloatingActionButton

                                    translateChunkViewModel.proposeTranslation(
                                        localChatRoom = translateChunkUIState.localChatRoom,
                                        originalChunk = translateChunkUIState.originalChunk,
                                        translationField = translationFieldState,
                                        onSuccess = { sessionId ->
                                            // Onto the session rather than back to
                                            // the chapter table. Nothing has been
                                            // translated yet -- the chunk is
                                            // translated when enough members sign --
                                            // so a table still showing it untranslated
                                            // would read as a failure.
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
                                                ImplementationPendingRoute("Failed Translation")
                                            )
                                        }
                                    )
                                }
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Propose translation"
                                )
                                Text("Propose Translation")
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
                    if (!translateChunkUIState.canSign) {
                        Text(
                            text = "This group has no shared key, so it cannot sign a " +
                                "translation. Run a shared key ceremony first.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

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
                    existingTranslationText = "",
                    canSign = true
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
