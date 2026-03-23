package ac.aux.compose.ui.composable

import ac.aux.compose.ui.composable.widgets.LoadingDataIndicator
import ac.aux.compose.ui.composable.widgets.feed.ListView
import ac.aux.compose.ui.composable.widgets.feed.LoadedFeedView
import ac.aux.compose.ui.theme.AuxTheme
import ac.aux.compose.ui.view.model.FeedListViewModel
import ac.aux.compose.ui.view.state.FeedListUIState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import kotlin.time.Clock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    initialFeedListUIState: FeedListUIState,
    onNavigateToEvent: (Event) -> Unit
) {
    val feedListViewModel: FeedListViewModel = viewModel(
        factory = FeedListViewModel.factory(
            initialFeedListUIState = initialFeedListUIState,
        ),
    )

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(
            scrollBehavior.nestedScrollConnection
        ),
        topBar = {
            MediumTopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary
                ),
                title = {
                    Text(
                        "Aux"
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {

                        }
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = "Account"
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )

        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).fillMaxSize()
        ) {
            when(val feedListUIState = feedListViewModel.feedListUIState) {
                is FeedListUIState.Loading -> {
                    LoadingDataIndicator()
                }
                FeedListUIState.Error -> {
                    Text("Something went wrong")
                }
                is FeedListUIState.Loaded -> {
                    LoadedFeedView(
                        events = feedListUIState.events,
                        onNavigateToEvent = onNavigateToEvent
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun FeedScreenPreview() {
    AuxTheme {
        Surface(
            modifier = Modifier.padding(20.dp)
        ) {
            FeedScreen(
                initialFeedListUIState = FeedListUIState.Loading,
                onNavigateToEvent = {}
            )
        }
    }
}