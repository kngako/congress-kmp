package ac.cord.auxiliary.compose.ui.composable

import ac.cord.auxiliary.compose.ui.composable.widgets.LoadingDataIndicator
import ac.cord.auxiliary.compose.ui.theme.AuxTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun LoadingScreen(
    text: String? = null
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding)
        ) {
            LoadingDataIndicator(
                text = text
            )
        }
    }
}

@Preview
@Composable
private fun LoadingScreenPreview() {
    AuxTheme {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            LoadingScreen(
                text = "Starting up"

            )
        }
    }
}