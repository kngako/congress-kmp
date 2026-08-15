package press.mantra.compose.ui.composable

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
fun UnqueuedProfileSynchronizationScreen(
    unsignedNostrEventId: Long,
    profilePublicKey: String,
    nostrRepository: press.mantra.compose.repository.NostrRepository
) {
    val unqueuedProfileSynchronizationViewModel: press.mantra.compose.ui.view.model.UnqueuedProfileSynchronizationViewModel = viewModel(
        factory = press.mantra.compose.ui.view.model.UnqueuedProfileSynchronizationViewModel.factory(
            unsignedNostrEventId = unsignedNostrEventId,
            profilePublicKey = profilePublicKey,
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
                    text = "We are searching the internet to find your profile and complete sign in.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "We are looking for your profile on as many relays as possible. Nostr aims to be decentralized by distributing data to multiple nodse.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )

                press.mantra.compose.ui.composable.widgets.LoadingDataIndicator(
                    fillScreen = false
                )

                Spacer(
                    modifier = Modifier.weight(2f)
                )
            }
        }
    }

    LaunchedEffect(true) {
        unqueuedProfileSynchronizationViewModel.queueSynchronization()
    }
}

@Preview
@Composable
private fun UnqueuedProfileSynchronizationScreenPreview() {
    press.mantra.compose.ui.theme.TorchTheme {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            UnqueuedProfileSynchronizationScreen(
                unsignedNostrEventId = 2,
                profilePublicKey = "npub is for living.",
                nostrRepository = press.mantra.compose.repository.NostrRepository.NO_OP_NOSTR_REPOSITORY
            )
        }
    }
}