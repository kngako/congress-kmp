package press.mantra.compose.ui.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import press.mantra.compose.ui.theme.spacing

@Composable
fun SocialPreconditionScreen(
    onNavigateToSkipForNow: () -> Unit,
    onNavigateToInviteFriend: () -> Unit,
    onNavigateToViewInvites: () -> Unit,
) {
    Scaffold(
        bottomBar = {
            BottomAppBar(
                modifier = Modifier,
                actions = {

                },
                floatingActionButton = {
                    ExtendedFloatingActionButton(
                        modifier = Modifier,
                        icon = {
                            Icon(
                                Icons.Default.SkipNext,
                                contentDescription = "Skip for now"
                            )
                        },
                        text = {
                            Text("Skip for now")
                        },
                        onClick = {
                            onNavigateToSkipForNow.invoke()
                        }
                    )
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).fillMaxSize()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.space250),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space125)
            ) {

                Text(
                    "Who will you be passing the aux to?",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(
                    modifier = Modifier.weight(1f)
                )

                Text(
                    "Tell friends to join you so your feed stays lively and fresh.",
                    textAlign = TextAlign.Center
                )

                Button(
                    onClick = {
                        onNavigateToInviteFriend.invoke()
                    }
                ) {
                    Text(
                        "Invite a friend"
                    )
                }

                Spacer(
                    modifier = Modifier.weight(1f)
                )

                Text(
                    "View and accept invites you may have received to stay connected with others.",
                    textAlign = TextAlign.Center
                )

                Button(
                    onClick = {
                        onNavigateToViewInvites.invoke()
                    }
                ) {
                    Text("View invites")
                }
            }
        }
    }
}

@Preview
@Composable
private fun SocialPreconditionScreenPreview() {
    press.mantra.compose.ui.theme.TorchTheme {
        Surface(
            modifier = Modifier.padding(MaterialTheme.spacing.space250)
        ) {
            SocialPreconditionScreen(
                onNavigateToSkipForNow = {},
                onNavigateToInviteFriend = {},
                onNavigateToViewInvites = {}
            )
        }
    }
}