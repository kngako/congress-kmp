package press.mantra.compose.ui.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import press.mantra.compose.database.model.ChatRoom
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.repository.ChatRepository
import press.mantra.compose.repository.MantraRepository
import press.mantra.compose.repository.NostrRepository
import press.mantra.compose.ui.composable.navigation.routes.ChatRoomDetailRoute
import press.mantra.compose.ui.composable.navigation.routes.ImplementationPendingRoute
import press.mantra.compose.ui.composable.navigation.routes.Route
import press.mantra.compose.ui.composable.widgets.LoadingDataIndicator
import press.mantra.compose.ui.theme.TorchTheme
import press.mantra.compose.ui.view.model.AddDialectViewModel
import press.mantra.compose.ui.view.state.AddDialectUIState

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AddDialectScreen(
    activeUserPublicKey: HexKey,
    chatRoomId: String,
    relayHint: String?,
    initialAddDialectUIState: AddDialectUIState = AddDialectUIState.Loading,
    nostrRepository: NostrRepository,
    chatRepository: ChatRepository,
    mantraRepository: MantraRepository,
    onNavigateToRouteAndPopUpInclusive: (Route) -> Unit,
    onNavigateToRoute: (Route) -> Unit,
) {
    val addDialectViewModel: AddDialectViewModel = viewModel(
        factory = AddDialectViewModel.factory(
            chatRoomId = chatRoomId,
            relayHint = relayHint,
            initialAddDialectUIState = initialAddDialectUIState,
            nostrRepository = nostrRepository,
            chatRepository = chatRepository,
            activeUserPublicKey = activeUserPublicKey,
            mantraRepository = mantraRepository
        )
    )

    when (val addDialectUIState = addDialectViewModel.addDialectUIState) {
        is AddDialectUIState.Error -> {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(
                    modifier = Modifier.height(50.dp)
                )
                Text(
                    text = addDialectUIState.message,
                )
            }
        }
        is AddDialectUIState.Loaded -> {
            val nameFieldState = rememberTextFieldState()
            val countryFieldState = rememberTextFieldState()
            val languageFieldState = rememberTextFieldState()

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            addDialectUIState.localChatRoom.RenderChatRoomTitleText()
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
                                    addDialectViewModel.addDialect(
                                        localChatRoom = addDialectUIState.localChatRoom,
                                        nameField = nameFieldState,
                                        countryField = countryFieldState,
                                        languageField = languageFieldState,
                                        onSuccess = {
                                            // Back to the group, reloaded so the new
                                            // dialect shows up in the list.
                                            onNavigateToRouteAndPopUpInclusive.invoke(
                                                ChatRoomDetailRoute(
                                                    activeUserPublicKey = activeUserPublicKey,
                                                    chatRoomId = chatRoomId,
                                                    relayHint = relayHint
                                                )
                                            )
                                        },
                                        onFailure = {
                                            onNavigateToRoute.invoke(
                                                ImplementationPendingRoute("Failed Dialect")
                                            )
                                        }
                                    )
                                }
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Add dialect"
                                )
                                Text("Add Dialect")
                            }
                        }
                    )
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier.padding(innerPadding).fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Add a dialect the group can translate into")

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
                            state = countryFieldState,
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
                            state = languageFieldState,
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
                }
            }
        }
        AddDialectUIState.Loading -> {
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
                    text = "Add Dialect",
                    style = MaterialTheme.typography.bodyLarge,
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
        if (initialAddDialectUIState == AddDialectUIState.Loading) {
            addDialectViewModel.initiateAddDialect()
        }
    }
}

@Preview
@Composable
private fun AddDialectScreenPreview() {
    TorchTheme {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            AddDialectScreen(
                activeUserPublicKey = "",
                chatRoomId = "publicKey",
                relayHint = null,
                initialAddDialectUIState = AddDialectUIState.Loaded(
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
