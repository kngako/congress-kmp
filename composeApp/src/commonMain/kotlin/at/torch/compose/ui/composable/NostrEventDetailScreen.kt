package at.torch.compose.ui.composable

import at.torch.compose.database.model.NostrEvent
import at.torch.compose.database.model.Profile
import at.torch.compose.database.model.intermdiate.LocalNostrEvent
import at.torch.compose.ui.composable.widgets.LoadingDataIndicator
import at.torch.compose.ui.composable.widgets.detail.MetadataEventDetail
import at.torch.compose.ui.composable.widgets.detail.TextNoteEventDetail
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NostrEventDetailScreen(
    initialNostrEventDetailUIState: at.torch.compose.ui.view.state.NostrEventDetailUIState,
    activeUserPublicKey: HexKey,
    nostrEventId: HexKey,
    nostrRepository: at.torch.compose.repository.NostrRepository,
    onNavigateBack: () -> Unit,
    onNavigateToEvent: (at.torch.compose.ui.composable.navigation.routes.Route) -> Unit,
    onNavigateToDirectMessage: (at.torch.compose.ui.composable.navigation.routes.Route) -> Unit,
    onNavigateToEditProfile: () -> Unit,
    onNavigateToWriteAReply: (String) -> Unit,
    onNavigateToQuoteNostrEvent: (String) -> Unit,
) {
    val nostrEventDetailViewModel: at.torch.compose.ui.view.model.NostrEventDetailViewModel = viewModel(
        factory = _root_ide_package_.at.torch.compose.ui.view.model.NostrEventDetailViewModel.Companion.factory(
            nostrEventId = nostrEventId,
            initialNostrEventDetailUIState = initialNostrEventDetailUIState,
            nostrRepository = nostrRepository,
            activeUserPublicKey = activeUserPublicKey
        ),
    )

    when(val feedListUIState = nostrEventDetailViewModel.nostrEventDetailUIState) {
        _root_ide_package_.at.torch.compose.ui.view.state.NostrEventDetailUIState.Error -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Something went wrong")
            }
        }
        is at.torch.compose.ui.view.state.NostrEventDetailUIState.Loaded -> {

            when(feedListUIState.localNostrEvent.nostrEvent.kind) {
                MetadataEvent.KIND -> {
                    _root_ide_package_.ac.cord.compose.ui.composable.widgets.detail.MetadataEventDetail(
                        activeUserPublicKey = activeUserPublicKey,
                        localNostrEvent = feedListUIState.localNostrEvent,
                        onNavigateBack = onNavigateBack,
                        onNavigateToEvent = onNavigateToEvent,
                        onNavigateToChatRoom = onNavigateToDirectMessage,
                        onNavigateToEditProfile = onNavigateToEditProfile,
                        nostrRepository = nostrRepository
                    )
                }
                TextNoteEvent.KIND -> {
                    _root_ide_package_.ac.cord.compose.ui.composable.widgets.detail.TextNoteEventDetail(
                        feedListUIState.localNostrEvent,
                        onNavigateBack = onNavigateBack,
                        onNavigateToWriteAReply = onNavigateToWriteAReply,
                        onNavigateToNostrEvent = onNavigateToQuoteNostrEvent,
                        nostrRepository = nostrRepository
                    )
                }
                RepostEvent.KIND -> {
                    // TODO: RePost...
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(feedListUIState.localNostrEvent.nostrEvent.content)

                        Text("post functionality coming soon.")
                    }
                }
                else -> {
                    Scaffold { innerPadding ->
                        Column(
                            modifier = Modifier.padding(innerPadding).fillMaxSize()
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(feedListUIState.localNostrEvent.nostrEvent.content)

                                Text("functionality coming soon.")
                            }
                        }
                    }
                }
            }
        }
        _root_ide_package_.at.torch.compose.ui.view.state.NostrEventDetailUIState.Loading -> {
            _root_ide_package_.ac.cord.compose.ui.composable.widgets.LoadingDataIndicator()

            LaunchedEffect(true) {
                nostrEventDetailViewModel.loadNostrFeed()
            }
        }
        _root_ide_package_.at.torch.compose.ui.view.state.NostrEventDetailUIState.NotFound -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("We couldn't find your nostr event. Please try again later.")
            }
        }
    }
}

@Preview
@Composable
private fun NostrEventDetailScreenPreview() {
    _root_ide_package_.ac.cord.compose.ui.theme.AuxTheme {
        Surface(
            modifier = Modifier.padding(20.dp)
        ) {
            NostrEventDetailScreen(
                initialNostrEventDetailUIState = _root_ide_package_.at.torch.compose.ui.view.state.NostrEventDetailUIState.Loaded(
                    localNostrEvent = _root_ide_package_.ac.cord.compose.database.model.intermdiate.LocalNostrEvent(
                        nostrEvent = _root_ide_package_.ac.cord.compose.database.model.NostrEvent(
                            id = "eventId",
                            pubKey = "publicKey",
                            content = """Lorem Ipsum is simply dummy text of the printing and typesetting industry. 
                                
Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown printer took a galley of type and scrambled it to make a type specimen book. 
                                
It has survived not only five centuries, but also the leap into electronic typesetting, remaining essentially unchanged. It was popularised in the 1960s with the release of Letraset sheets containing Lorem Ipsum passages, and more recently with desktop publishing software like Aldus PageMaker including versions of Lorem Ipsum.""".trimIndent(),
                            tags = emptyArray(),
                            sig = "",
                            kind = TextNoteEvent.KIND
                        ),
                        profile = _root_ide_package_.ac.cord.compose.database.model.Profile(
                            publicKey = "pubKey",
                            displayName = "John Doe",
                            nostrEventId = "nostrEventId"
                        )
                    )
                ),
                nostrEventId = "",
                activeUserPublicKey = "",
                nostrRepository = _root_ide_package_.at.torch.compose.repository.NostrRepository.Companion.NO_OP_NOSTR_REPOSITORY,
                onNavigateBack = {},
                onNavigateToEvent = {},
                onNavigateToWriteAReply = {},
                onNavigateToEditProfile = {},
                onNavigateToQuoteNostrEvent = {},
                onNavigateToDirectMessage = {}
            )
        }
    }
}