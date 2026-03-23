package ac.aux.compose.ui.composable

import ac.aux.compose.ui.theme.AuxTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun FeedScreen(
    text: String
) {

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).fillMaxSize()
        ) {
            // Notes
            // Stories/Reels
            // Pictures
            // Private Messages (deferred to another app)
            // Notifications
            Column(
                modifier = Modifier.padding(20.dp).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "\"$text\" functionality coming soon",

                )
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
                "Feed"
            )
        }
    }
}