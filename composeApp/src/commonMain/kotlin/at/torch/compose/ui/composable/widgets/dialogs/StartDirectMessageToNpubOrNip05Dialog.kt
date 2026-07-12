package at.torch.compose.ui.composable.widgets.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Start
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import at.torch.compose.ui.composable.navigation.routes.ImplementationPendingRoute
import at.torch.compose.ui.composable.navigation.routes.Route
import fr.acinq.phoenix.utils.Parser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun StartDirectMessageToNpubOrNip05Dialog(
    scope: CoroutineScope,
    toggleOpenDialogSetting: (Boolean) -> Unit,
    onNavigateToRoute: (Route) -> Unit
) {

    Dialog(
        onDismissRequest = {

        }
    ) {
        val startChatButtonEnabled = remember { mutableStateOf(false) }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            val textFieldState = rememberTextFieldState()
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TextField(
                    state = textFieldState,
                    placeholder = {
                        Text("Input npub... or nip05")
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                scope.launch {

                                    toggleOpenDialogSetting.invoke(false)
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close Start Npub Direct Message"
                            )
                        }
                    },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    inputTransformation = {
                        val input = asCharSequence().toString().lowercase()

                        if (input.startsWith("npub1") || input.startsWith("nostr:npub1")) {
                            // Check for valid npub
                            startChatButtonEnabled.value = true
                        } else if (Parser.parseEmailLikeAddress(input) != null) {
                            // Check for valid npub
                            startChatButtonEnabled.value = true
                        } else {
                            startChatButtonEnabled.value = false
                        }
                    }
                )

                TextButton(
                    enabled = startChatButtonEnabled.value,
                    onClick = {
                        scope.launch {
                            toggleOpenDialogSetting.invoke(false)
                        }.invokeOnCompletion {
                            onNavigateToRoute.invoke(
                                ImplementationPendingRoute(
                                    "Direct Message to ${textFieldState.text}"
                                )
                            )
                        }
                    }
                ) {
                    Text("Start Chat")

                    Spacer(
                        modifier = Modifier.width(10.dp)
                    )

                    Icon(
                        Icons.Default.Start,
                        contentDescription = "Start chat"
                    )
                }
            }
        }
    }
}