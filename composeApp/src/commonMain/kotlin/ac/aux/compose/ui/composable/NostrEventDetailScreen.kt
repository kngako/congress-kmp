package ac.aux.compose.ui.composable

import ac.aux.compose.database.model.NostrEvent
import ac.aux.compose.database.model.Profile
import ac.aux.compose.database.model.intermdiate.LocalNostrEvent
import ac.aux.compose.extensions.toFormattedTimeAndDateString
import ac.aux.compose.repository.NostrRepository
import ac.aux.compose.ui.composable.widgets.LoadingDataIndicator
import ac.aux.compose.ui.composable.widgets.detail.TextNoteDetail
import ac.aux.compose.ui.theme.AuxTheme
import ac.aux.compose.ui.view.model.NostrEventDetailViewModel
import ac.aux.compose.ui.view.state.NostrEventDetailUIState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddReaction
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.Cyclone
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Money
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
    onNavigateToWriteAReply: (String) -> Unit
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
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(feedListUIState.localNostrEvent.nostrEvent.content)

                        Text("profile functionality coming soon.")
                    }
                }
                TextNoteEvent.KIND -> {
                    TextNoteDetail(
                        feedListUIState.localNostrEvent,
                        onNavigateBack = onNavigateBack,
                        onNavigateToWriteAReply = onNavigateToWriteAReply
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
                onNavigateToWriteAReply = {}
            )
        }
    }
}