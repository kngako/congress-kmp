package ac.cord.auxiliary.compose.ui.composable

import ac.cord.auxiliary.compose.database.model.NostrEvent
import ac.cord.auxiliary.compose.database.model.Profile
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalNostrEvent
import ac.cord.auxiliary.compose.repository.NostrRepository
import ac.cord.auxiliary.compose.ui.composable.navigation.routes.Route
import ac.cord.auxiliary.compose.ui.composable.widgets.LoadingDataIndicator
import ac.cord.auxiliary.compose.ui.composable.widgets.detail.MetadataEventDetail
import ac.cord.auxiliary.compose.ui.composable.widgets.detail.TextNoteEventDetail
import ac.cord.auxiliary.compose.ui.theme.AuxTheme
import ac.cord.auxiliary.compose.ui.view.model.NostrEventDetailViewModel
import ac.cord.auxiliary.compose.ui.view.state.NostrEventDetailUIState
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
    initialNostrEventDetailUIState: NostrEventDetailUIState,
    nostrEventId: HexKey,
    nostrRepository: NostrRepository,
    onNavigateBack: () -> Unit,
    onNavigateToEvent: (Route) -> Unit,
    onNavigateToDirectMessage: (Route) -> Unit,
    onNavigateToEditProfile: () -> Unit,
    onNavigateToWriteAReply: (String) -> Unit,
    onNavigateToQuoteNostrEvent: (String) -> Unit,
) {
    val nostrEventDetailViewModel: NostrEventDetailViewModel = viewModel(
        factory = NostrEventDetailViewModel.factory(
            nostrEventId = nostrEventId,
            initialNostrEventDetailUIState = initialNostrEventDetailUIState,
            nostrRepository = nostrRepository
        ),
    )

    when(val feedListUIState = nostrEventDetailViewModel.nostrEventDetailUIState) {
        NostrEventDetailUIState.Error -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Something went wrong")
            }
        }
        is NostrEventDetailUIState.Loaded -> {

            when(feedListUIState.localNostrEvent.nostrEvent.kind) {
                MetadataEvent.KIND -> {
                    MetadataEventDetail(
                        localNostrEvent = feedListUIState.localNostrEvent,
                        onNavigateBack = onNavigateBack,
                        onNavigateToEvent = onNavigateToEvent,
                        onNavigateToChatRoom = onNavigateToDirectMessage,
                        onNavigateToEditProfile = onNavigateToEditProfile,
                        nostrRepository = nostrRepository
                    )
                }
                TextNoteEvent.KIND -> {
                    TextNoteEventDetail(
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
        NostrEventDetailUIState.Loading -> {
            LoadingDataIndicator()

            LaunchedEffect(true) {
                nostrEventDetailViewModel.loadNostrFeed()
            }
        }
        NostrEventDetailUIState.NotFound -> {
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
    AuxTheme {
        Surface(
            modifier = Modifier.padding(20.dp)
        ) {
            NostrEventDetailScreen(
                initialNostrEventDetailUIState = NostrEventDetailUIState.Loaded(
                    localNostrEvent = LocalNostrEvent(
                        nostrEvent = NostrEvent(
                            id = "eventId",
                            pubKey = "publicKey",
                            content = """Lorem Ipsum is simply dummy text of the printing and typesetting industry. 
                                
Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown printer took a galley of type and scrambled it to make a type specimen book. 
                                
It has survived not only five centuries, but also the leap into electronic typesetting, remaining essentially unchanged. It was popularised in the 1960s with the release of Letraset sheets containing Lorem Ipsum passages, and more recently with desktop publishing software like Aldus PageMaker including versions of Lorem Ipsum.""".trimIndent(),
                            tags = emptyArray(),
                            sig = "",
                            kind = TextNoteEvent.KIND
                        ),
                        profile = Profile(
                            publicKey = "pubKey",
                            displayName = "John Doe",
                            nostrEventId = "nostrEventId"
                        )
                    )
                ),
                nostrEventId = "",
                nostrRepository = NostrRepository.NO_OP_NOSTR_REPOSITORY,
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