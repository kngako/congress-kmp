package press.mantra.compose.ui.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import press.mantra.compose.ui.theme.spacing
import mantra.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import mantra.composeapp.generated.resources.sign_in_is_not_yet_available_while_mantra_is
import press.mantra.compose.ui.composable.widgets.LocalSnackbarHostState
import press.mantra.compose.ui.theme.readableContent
import press.mantra.compose.ui.theme.ConformancePreviews

// The message is the whole screen while sign in is held back, so it is centred in
// the window rather than given the leading edge the readable column normally keeps.
@Composable
fun SignInToProfileScreen() {
    Scaffold(snackbarHost = { SnackbarHost(LocalSnackbarHostState.current) }) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding)
            .readableContent()
            .padding(MaterialTheme.spacing.screenMargin),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(Res.string.sign_in_is_not_yet_available_while_mantra_is),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
        }
    }
}

@ConformancePreviews
@Composable
private fun SignInToProfileScreenPreview() {
    press.mantra.compose.ui.theme.TorchTheme {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            SignInToProfileScreen()
        }
    }
}
