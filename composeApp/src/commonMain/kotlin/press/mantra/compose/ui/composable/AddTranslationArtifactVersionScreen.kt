package press.mantra.compose.ui.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import press.mantra.compose.database.model.ChatRoom
import press.mantra.compose.database.model.MantraArtifact
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.repository.ChatRepository
import press.mantra.compose.repository.MantraRepository
import press.mantra.compose.ui.composable.navigation.routes.ArtifactDetailRoute
import press.mantra.compose.ui.composable.navigation.routes.Route
import press.mantra.compose.ui.composable.widgets.LoadingDataIndicator
import press.mantra.compose.ui.theme.TorchTheme
import press.mantra.compose.ui.view.model.AddTranslationArtifactVersionViewModel
import press.mantra.compose.ui.view.state.AddTranslationArtifactVersionUIState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTranslationArtifactVersionScreen(
    activeUserPublicKey: HexKey,
    artifactId: String,
    chatRoomId: String,
    relayHint: String?,
    initialAddTranslationArtifactVersionUIState: AddTranslationArtifactVersionUIState = AddTranslationArtifactVersionUIState.Loading,
    mantraRepository: MantraRepository,
    chatRepository: ChatRepository,
    onNavigateToRoute: (Route) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val addTranslationArtifactVersionViewModel: AddTranslationArtifactVersionViewModel = viewModel(
        factory = AddTranslationArtifactVersionViewModel.factory(
            activeUserPublicKey = activeUserPublicKey,
            artifactId = artifactId,
            chatRoomId = chatRoomId,
            relayHint = relayHint,
            initialAddTranslationArtifactVersionUIState = initialAddTranslationArtifactVersionUIState,
            mantraRepository = mantraRepository,
            chatRepository = chatRepository
        )
    )

    when (val addTranslationUIState = addTranslationArtifactVersionViewModel.addTranslationArtifactVersionUIState) {
        is AddTranslationArtifactVersionUIState.Error -> {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(50.dp))
                Text(text = addTranslationUIState.message)
            }
        }

        is AddTranslationArtifactVersionUIState.Loaded -> {
            val dialectNameFieldState = rememberTextFieldState()
            val dialectCountryFieldState = rememberTextFieldState()
            val dialectLanguageFieldState = rememberTextFieldState()

            // null = "New dialect" (show the create fields); otherwise reuse an
            // existing dialect by id.
            var selectedDialectId: String? by remember { mutableStateOf(null) }

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Translate ${addTranslationUIState.artifact.name}") },
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
                                    addTranslationArtifactVersionViewModel.addTranslation(
                                        localChatRoom = addTranslationUIState.localChatRoom,
                                        existingDialectId = selectedDialectId,
                                        dialectNameField = dialectNameFieldState,
                                        dialectCountryField = dialectCountryFieldState,
                                        dialectLanguageField = dialectLanguageFieldState,
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
                                    contentDescription = "Add translation"
                                )
                                Text("Add Translation")
                            }
                        }
                    )
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier.padding(innerPadding).fillMaxSize().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Translate into which dialect?")

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        addTranslationUIState.dialects.forEach { dialect ->
                            FilterChip(
                                selected = selectedDialectId == dialect.id,
                                onClick = { selectedDialectId = dialect.id },
                                label = { Text(dialect.name) }
                            )
                        }
                        FilterChip(
                            selected = selectedDialectId == null,
                            onClick = { selectedDialectId = null },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Create a new dialect"
                                )
                            },
                            label = { Text("New dialect") }
                        )
                    }

                    // Only collect new-dialect details when not reusing an existing one.
                    if (selectedDialectId == null) {
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            state = dialectNameFieldState,
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Title,
                                    contentDescription = "Name of the dialect"
                                )
                            },
                            label = { Text("Dialect Name") },
                            placeholder = { Text("eg. Sesotho") },
                        )

                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            state = dialectCountryFieldState,
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Public,
                                    contentDescription = "Country of the dialect"
                                )
                            },
                            label = { Text("Country") },
                            placeholder = { Text("eg. Lesotho") },
                        )

                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            state = dialectLanguageFieldState,
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Translate,
                                    contentDescription = "Language of the dialect"
                                )
                            },
                            label = { Text("Language") },
                            placeholder = { Text("eg. st") },
                        )
                    }

                    Text(
                        text = "The artifact's chapters and chunks will be scaffolded for this translation.",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        AddTranslationArtifactVersionUIState.Loading -> {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Add Translation",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                LoadingDataIndicator(fillScreen = false)
                Spacer(modifier = Modifier.weight(2f))
            }
        }
    }

    LaunchedEffect(true) {
        if (initialAddTranslationArtifactVersionUIState == AddTranslationArtifactVersionUIState.Loading) {
            addTranslationArtifactVersionViewModel.initiateAddTranslation()
        }
    }
}

@Preview
@Composable
private fun AddTranslationArtifactVersionScreenPreview() {
    TorchTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            AddTranslationArtifactVersionScreen(
                activeUserPublicKey = "",
                artifactId = "artifactId",
                chatRoomId = "chatRoomId",
                relayHint = null,
                initialAddTranslationArtifactVersionUIState = AddTranslationArtifactVersionUIState.Loaded(
                    localChatRoom = LocalChatRoom(
                        chatRoom = ChatRoom(
                            id = "",
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
                    )
                ),
                mantraRepository = MantraRepository.NO_OP_MANTRA_REPOSITORY,
                chatRepository = ChatRepository.NO_OP_CHAT_REPOSITORY,
                onNavigateToRoute = {},
                onNavigateBack = {}
            )
        }
    }
}
