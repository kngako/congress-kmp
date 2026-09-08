package press.mantra.compose.ui.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import press.mantra.compose.ui.theme.spacing
import mantra.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import mantra.composeapp.generated.resources.functionality_coming_soon_2
import androidx.compose.material3.SnackbarHost
import press.mantra.compose.ui.composable.widgets.LocalSnackbarHostState
import press.mantra.compose.ui.theme.readableContent

@Composable
fun ImplementationPendingScreen(
    text: String
) {

    Scaffold(snackbarHost = { SnackbarHost(LocalSnackbarHostState.current) }) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).readableContent().fillMaxSize()
        ) {
            Column(
                modifier = Modifier.padding(MaterialTheme.spacing.space250).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    stringResource(Res.string.functionality_coming_soon_2, text),

                )
            }
        }
    }
}

@Preview
@Composable
private fun ImplementationPendingScreenPreview() {
    press.mantra.compose.ui.theme.TorchTheme {
        Surface(
            modifier = Modifier.padding(MaterialTheme.spacing.space250)
        ) {
            ImplementationPendingScreen(
                "Sign in"
            )
        }
    }
}