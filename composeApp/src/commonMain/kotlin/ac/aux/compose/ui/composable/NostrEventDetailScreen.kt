package ac.aux.compose.ui.composable

import ac.aux.compose.repository.NostrRepository
import ac.aux.compose.ui.composable.widgets.LoadingDataIndicator
import ac.aux.compose.ui.theme.AuxTheme
import ac.aux.compose.ui.view.model.NostrEventDetailViewModel
import ac.aux.compose.ui.view.state.NostrEventDetailUIState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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

@Composable
fun NostrEventDetailScreen(
    initialNostrEventDetailUIState: NostrEventDetailUIState,
    nostrEventId: HexKey,
    nostrRepository: NostrRepository
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
                    // TODO: Post...
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(feedListUIState.localNostrEvent.nostrEvent.content)

                        Text("post functionality coming soon.")
                    }
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
                initialNostrEventDetailUIState = NostrEventDetailUIState.Loading,
                nostrEventId = "",
                nostrRepository = NostrRepository.NO_OP_NOSTR_REPOSITORY
            )
        }
    }
}