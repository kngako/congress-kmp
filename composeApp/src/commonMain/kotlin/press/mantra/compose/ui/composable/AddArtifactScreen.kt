package press.mantra.compose.ui.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import press.mantra.compose.database.model.ChatRoom
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.repository.ChatRepository
import press.mantra.compose.repository.NostrRepository
import press.mantra.compose.ui.composable.navigation.routes.ChatRoomDetailRoute
import press.mantra.compose.ui.composable.navigation.routes.Route
import press.mantra.compose.ui.composable.widgets.LoadingDataIndicator
import press.mantra.compose.ui.theme.TorchTheme
import press.mantra.compose.ui.view.state.ChatRoomMessagingUIState
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import press.mantra.compose.repository.MantraRepository
import press.mantra.compose.ui.composable.navigation.routes.ImplementationPendingRoute
import press.mantra.compose.ui.view.model.AddArtifactViewModel
import press.mantra.compose.ui.view.state.AddArtifactUIState

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddArtifactScreen(
    activeUserPublicKey: HexKey,
    chatRoomId: String,
    relayHint: String?,
    initialAddArtifactUIState: AddArtifactUIState = AddArtifactUIState.Loading,
    nostrRepository: NostrRepository,
    chatRepository: ChatRepository,
    mantraRepository: MantraRepository,
    onNavigateToRouteAndPopUpInclusive: (Route) -> Unit,
    onNavigateToRoute: (Route) -> Unit,
) {
    val addArtifactViewModel: AddArtifactViewModel = viewModel(
        factory = AddArtifactViewModel.factory(
            chatRoomId = chatRoomId,
            relayHint = relayHint,
            initialAddArtifactUIState = initialAddArtifactUIState,
            nostrRepository = nostrRepository,
            chatRepository = chatRepository,
            activeUserPublicKey = activeUserPublicKey,
            mantraRepository = mantraRepository
        )
    )

    when (val addArtifactUIState = addArtifactViewModel.addArtifactUIState) {
        is AddArtifactUIState.Error -> {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(
                    modifier = Modifier.height(50.dp)
                )
                Text(
                    text = addArtifactUIState.message,
                )
            }
        }
        is AddArtifactUIState.Loaded -> {
            val nameFieldState = rememberTextFieldState()
            val urlFieldState = rememberTextFieldState()
            val versionLabelFieldState = rememberTextFieldState("1.0")
            val dialectNameFieldState = rememberTextFieldState()
            val dialectCountryFieldState = rememberTextFieldState()
            val dialectLanguageFieldState = rememberTextFieldState()

            // null = "New dialect" (show the create fields); otherwise the id of
            // an existing dialect to reuse.
            var selectedDialectId: String? by remember { mutableStateOf(null) }

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            addArtifactUIState.localChatRoom.RenderChatRoomTitleText()
                        },
                        actions = {

                        }
                    )
                },
                bottomBar = {
                    BottomAppBar(
                        actions = {},
                        floatingActionButton = {
                            ExtendedFloatingActionButton(
                                onClick = {
                                    addArtifactViewModel.addArtifact(
                                        localChatRoom = addArtifactUIState.localChatRoom,
                                        nameField = nameFieldState,
                                        urlField = urlFieldState,
                                        versionLabelField = versionLabelFieldState,
                                        existingDialectId = selectedDialectId,
                                        dialectNameField = dialectNameFieldState,
                                        dialectCountryField = dialectCountryFieldState,
                                        dialectLanguageField = dialectLanguageFieldState,
                                        onSuccess = {
                                            onNavigateToRoute.invoke(
                                                ImplementationPendingRoute("Add Artifact")
                                            )
                                        },
                                        onFailure = {
                                            onNavigateToRoute.invoke(
                                                ImplementationPendingRoute("Failed Artifact")
                                            )
                                        }
                                    )

                                }
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Add artifact"
                                )
                                Text("Add Artifact")
                            }
                        }
                    )
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier.padding(innerPadding).fillMaxSize()
                ) {
                    // TODO: Check that we have direct message relays for this user...
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Add Artifact to the group library")
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth()
                                .background(BottomAppBarDefaults.containerColor),
                            state = nameFieldState,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                disabledBorderColor = Color.Transparent
                            ),
                            leadingIcon = {
                                Icon(
                                    Icons.Default.DriveFileRenameOutline,
                                    contentDescription = "Name of the artifact"
                                )
                            },
                            label = {
                                Text(
                                    text = "Name of Artifact"
                                )
                            },
                            placeholder = {
                                Text(
                                    text = "eg. To Kill a Mocking Bird"
                                )
                            },
                        )

                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth()
                                .background(BottomAppBarDefaults.containerColor),
                            state = urlFieldState,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                disabledBorderColor = Color.Transparent
                            ),
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Link,
                                    contentDescription = "Link to the artifact"
                                )
                            },
                            label = {
                                Text(
                                    text = "Url"
                                )
                            },
                            placeholder = {
                                Text(
                                    text = "eg. https://harper.com/2-kill-Bird"
                                )
                            },
                        )

                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth()
                                .background(BottomAppBarDefaults.containerColor),
                            state = versionLabelFieldState,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                disabledBorderColor = Color.Transparent
                            ),
                            leadingIcon = {
                                Icon(
                                    Icons.Default.LocalOffer,
                                    contentDescription = "Verison label"
                                )
                            },
                            label = {
                                Text(
                                    text = "Initial Version Label"
                                )
                            },
                            placeholder = {
                                Text(
                                    text = "eg. First Edition"
                                )
                            },
                        )

                        Text("Source dialect")

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            addArtifactUIState.dialects.forEach { dialect ->
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
                                modifier = Modifier.fillMaxWidth()
                                    .background(BottomAppBarDefaults.containerColor),
                                state = dialectNameFieldState,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    disabledBorderColor = Color.Transparent
                                ),
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Title,
                                        contentDescription = "Name of the dialect"
                                    )
                                },
                                label = {
                                    Text(
                                        text = "Dialect Name"
                                    )
                                },
                                placeholder = {
                                    Text(
                                        text = "eg. Sesotho"
                                    )
                                },
                            )

                            OutlinedTextField(
                                modifier = Modifier.fillMaxWidth()
                                    .background(BottomAppBarDefaults.containerColor),
                                state = dialectCountryFieldState,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    disabledBorderColor = Color.Transparent
                                ),
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Public,
                                        contentDescription = "Country of the dialect"
                                    )
                                },
                                label = {
                                    Text(
                                        text = "Country"
                                    )
                                },
                                placeholder = {
                                    Text(
                                        text = "eg. Lesotho"
                                    )
                                },
                            )

                            OutlinedTextField(
                                modifier = Modifier.fillMaxWidth()
                                    .background(BottomAppBarDefaults.containerColor),
                                state = dialectLanguageFieldState,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    disabledBorderColor = Color.Transparent
                                ),
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Translate,
                                        contentDescription = "Language of the dialect"
                                    )
                                },
                                label = {
                                    Text(
                                        text = "Language"
                                    )
                                },
                                placeholder = {
                                    Text(
                                        text = "eg. st"
                                    )
                                },
                            )
                        }

                        // TODO: Add Visibility
                    }
                }
            }
        }
        AddArtifactUIState.Loading -> {
            Column(
                modifier = Modifier.fillMaxWidth().padding(
                    20.dp
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {

                Spacer(
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Direct Message Detail",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Direct message functionality will be here.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )

                LoadingDataIndicator(
                    fillScreen = false
                )

                Spacer(
                    modifier = Modifier.weight(2f)
                )
            }
        }
    }

    LaunchedEffect(true) {
        if (initialAddArtifactUIState == AddArtifactUIState.Loading) {
            addArtifactViewModel.initiateChatRoomDetail()
        }
    }
}

@Preview
@Composable
private fun AddArtifactScreenPreview() {
    TorchTheme {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            AddArtifactScreen(
                activeUserPublicKey = "",
                chatRoomId = "publicKey",
                relayHint = null,
                initialAddArtifactUIState = AddArtifactUIState.Loaded(
                    localChatRoom = LocalChatRoom(
                        chatRoom = ChatRoom(
                            id = "",
                            userPublicKey = "",
                            subject = "Message title",
                            description = "See something. Say somethin",
                            initialGiftWrapPayloadId = "sdfaer",
                            mlsGroupState = null
                        ),
                    )
                ),
                nostrRepository = NostrRepository.NO_OP_NOSTR_REPOSITORY,
                chatRepository = ChatRepository.NO_OP_CHAT_REPOSITORY,
                mantraRepository = MantraRepository.NO_OP_MANTRA_REPOSITORY,
                onNavigateToRouteAndPopUpInclusive = {},
                onNavigateToRoute = {}
            )
        }
    }
}