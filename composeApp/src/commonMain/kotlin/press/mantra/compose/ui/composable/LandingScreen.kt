package press.mantra.compose.ui.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun LandingScreen(
    onNavigateToLearnMore: () -> Unit,
    onNavigateToSignIn: () -> Unit,
    onNavigateToCreateProfile: () -> Unit
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Torch",
                    style = MaterialTheme.typography.headlineLarge
                )

                Text(
                    text = "Keep the feed alive."
                )

                TextButton(
                    onClick = onNavigateToLearnMore
                ) {
                    Text(
                        text = "Learn More",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                Spacer(
                    modifier = Modifier.weight(1f)
                )

                Button(
                    onClick = onNavigateToSignIn
                ) {

                    Text(
                        "Sign in",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                Text(
                    text = "Sign in to Torch via nsec, or remote signer",
                    modifier = Modifier.padding(10.dp),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center
                )

                Spacer(
                    modifier = Modifier.height(20.dp)
                )

                Button(
                    onClick = onNavigateToCreateProfile
                ) {
                    Text(
                        "Create Profile",
                        style = MaterialTheme.typography.bodyLarge
                    )

                }
                Text(
                    text = "If you are new to Torch or just want to create a fresh profile",
                    modifier = Modifier.padding(10.dp),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Preview
@Composable
private fun LandingScreenPreview() {
    _root_ide_package_.press.mantra.compose.ui.theme.TorchTheme {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            LandingScreen(
                onNavigateToSignIn = {},
                onNavigateToLearnMore = {},
                onNavigateToCreateProfile = {}
            )
        }
    }
}