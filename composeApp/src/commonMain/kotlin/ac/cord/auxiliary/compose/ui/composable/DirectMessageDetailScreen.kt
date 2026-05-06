package ac.cord.auxiliary.compose.ui.composable

import ac.cord.auxiliary.compose.repository.NostrRepository
import ac.cord.auxiliary.compose.ui.composable.widgets.LoadingDataIndicator
import ac.cord.auxiliary.compose.ui.theme.AuxTheme
import ac.cord.auxiliary.compose.ui.view.model.DirectMessageDetailViewModel
import ac.cord.auxiliary.compose.ui.view.model.UnqueuedProfileSynchronizationViewModel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DirectMessageDetailScreen(
    nostrRepository: NostrRepository
) {
    val directMessageDetailViewModel: DirectMessageDetailViewModel = viewModel(
        factory = DirectMessageDetailViewModel.factory(
            nostrRepository = nostrRepository
        )
    )

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding)
        ) {

            Column(
                modifier = Modifier.fillMaxWidth().padding(
                    10.dp
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
        directMessageDetailViewModel.queueSynchronization()
    }
}

@Preview
@Composable
private fun UnqueuedProfileSynchronizationScreenPreview() {
    AuxTheme {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            DirectMessageDetailScreen(
                nostrRepository = NostrRepository.NO_OP_NOSTR_REPOSITORY
            )
        }
    }
}