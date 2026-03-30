package ac.aux.compose.ui.composable

import ac.aux.compose.database.model.NostrEvent
import ac.aux.compose.database.model.Profile
import ac.aux.compose.database.model.UnsignedNostrEvent
import ac.aux.compose.repository.NostrRepository
import ac.aux.compose.ui.composable.navigation.routes.UnannouncedProfileRoute
import ac.aux.compose.ui.composable.widgets.LoadingDataIndicator
import ac.aux.compose.ui.theme.AuxTheme
import ac.aux.compose.ui.theme.BluePill
import ac.aux.compose.ui.theme.RedPill
import ac.aux.compose.ui.view.model.CreateProfileViewModel
import ac.aux.compose.ui.view.state.CreateProfileUIState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UnsignedProfileScreen(
    unsignedNostrEvent: UnsignedNostrEvent
) {
    val metadataEvent = MetadataEvent(
        id = unsignedNostrEvent.id.toString(),
        pubKey = unsignedNostrEvent.pubKey,
        createdAt = unsignedNostrEvent.createdAt.epochSeconds,
        tags = unsignedNostrEvent.tags,
        content = unsignedNostrEvent.content,
        sig = ""
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
                    text = "Your profile is almost ready... just getting it's first cryptographic signature together.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "As long as you control your keys there can be no dispute about who ${metadataEvent.contactMetaData()?.displayName ?: "YOU"} actually is.",
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
}

@Preview
@Composable
private fun UnsignedProfileScreenPreview() {
    AuxTheme {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            UnsignedProfileScreen(
                unsignedNostrEvent = UnsignedNostrEvent(
                    pubKey = "",
                    kind = 0,
                    content = "Something here",
                    tags = emptyArray(),
                )
            )
        }
    }
}