package at.torch.compose.ui.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import at.torch.compose.database.model.NostrEvent
import at.torch.compose.database.model.intermdiate.LocalNostrEvent
import at.torch.compose.extensions.hexToNpubHrp
import at.torch.compose.repository.NostrRepository
import at.torch.compose.ui.composable.navigation.routes.ImplementationPendingRoute
import at.torch.compose.ui.composable.navigation.routes.Route
import at.torch.compose.ui.composable.widgets.LoadingDataIndicator
import at.torch.compose.ui.composable.widgets.QRCodeView
import at.torch.compose.ui.composable.widgets.profile.ProfileAvatar
import at.torch.compose.ui.view.model.ShareProfileViewModel
import at.torch.compose.ui.view.state.ShareProfileUIState
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ShareProfileScreen(
    initialShareProfileUIState: ShareProfileUIState = ShareProfileUIState.Loading,
    activeUserPublicKey: HexKey,
    nostrEventId: HexKey,
    onNavigateBack: () -> Unit,
    onNavigateToRoute: (Route) -> Unit,
    nostrRepository: NostrRepository,
) {

    val shareProfileViewModel: ShareProfileViewModel = viewModel(
        factory = ShareProfileViewModel.factory(
            nostrEventId = nostrEventId,
            initialActiveProfileUIState = initialShareProfileUIState,
            nostrRepository = nostrRepository,
            activeUserPublicKey = activeUserPublicKey
        ),
    )

    when(val shareProfileUIState = shareProfileViewModel.shareProfileUIState) {
        ShareProfileUIState.Error -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Something went wrong")
            }
        }
        is ShareProfileUIState.Loaded -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = "Profile"
                            )
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    onNavigateBack.invoke()
                                }
                            ) {
                                Icon(
                                    Icons.Default.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        }
                    )
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier.padding(innerPadding)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ProfileAvatar(
                                    profile = shareProfileUIState.localNostrEvent.profile,
                                    publicKey = activeUserPublicKey
                                )

                                Column {
                                    shareProfileUIState.localNostrEvent.profile?.humanReadableNameOrPubkey()?.let { humanReadableNameOrPubkey ->
                                        Text(
                                            text = humanReadableNameOrPubkey,
                                            style = MaterialTheme.typography.titleLarge
                                        )
                                    }
                                    shareProfileUIState.localNostrEvent.profile?.about?.let {
                                        Text(
                                            text = it,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier.padding(40.dp)
                            ) {
                                QRCodeView(
                                    activeUserPublicKey.hexToNpubHrp()
                                )
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(20.dp)
                            ) {
                                val clipboardManager = LocalClipboardManager.current

                                TextButton(
                                    onClick = {

                                        clipboardManager.setText(
                                            buildAnnotatedString {
                                                append(activeUserPublicKey.hexToNpubHrp())
                                            }
                                        )
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "Copy npub"
                                    )

                                    Spacer(
                                        modifier = Modifier.width(10.dp)
                                    )
                                    Text(
                                        text = activeUserPublicKey.hexToNpubHrp(),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }

                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Button(
                            onClick = {
                                shareProfileViewModel.rebroadcastProfile(
                                    localNostrEvent = shareProfileUIState.localNostrEvent
                                )
                            }
                        ) {
                            Text(
                                "Re-broadcast"
                            )
                        }
                    }
                }
            }
        }
        ShareProfileUIState.Loading -> {
            LoadingDataIndicator()

            LaunchedEffect(true) {
                shareProfileViewModel.loadNostrFeed()
            }
        }
        ShareProfileUIState.NotFound -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("We couldn't find the local profile. Please try again later.")
            }
        }
    }
}

@Preview
@Composable
private fun UnannouncedProfileScreenPreview() {
    _root_ide_package_.at.torch.compose.ui.theme.TorchTheme {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            ShareProfileScreen(
                initialShareProfileUIState = ShareProfileUIState.Loaded(
                    localNostrEvent = LocalNostrEvent(
                        nostrEvent = NostrEvent(
                            id = "eventId",
                            pubKey = "84dee6e676e5bb67b4ad4e042cf70cbd8681155db535942fcc6a0533858a7240",
                            content = """Lorem Ipsum is simply dummy text of the printing and typesetting industry. 
                                
Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown printer took a galley of type and scrambled it to make a type specimen book. 
                                
It has survived not only five centuries, but also the leap into electronic typesetting, remaining essentially unchanged. It was popularised in the 1960s with the release of Letraset sheets containing Lorem Ipsum passages, and more recently with desktop publishing software like Aldus PageMaker including versions of Lorem Ipsum.""".trimIndent(),
                            tags = emptyArray(),
                            sig = "",
                            kind = MetadataEvent.KIND
                        ),
                        profile = _root_ide_package_.at.torch.compose.database.model.Profile(
                            publicKey = "84dee6e676e5bb67b4ad4e042cf70cbd8681155db535942fcc6a0533858a7240",
                            displayName = "John Doe",
                            nostrEventId = "nostrEventId",
                            about = "I been here, there and everywhere."
                        )

                    )
                ),
                activeUserPublicKey = "84dee6e676e5bb67b4ad4e042cf70cbd8681155db535942fcc6a0533858a7240",
                nostrEventId = "",
                nostrRepository = NostrRepository.NO_OP_NOSTR_REPOSITORY,
                onNavigateToRoute = {},
                onNavigateBack = {}
            )
        }
    }
}