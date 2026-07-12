package at.torch.compose.ui.composable.widgets.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import at.torch.compose.extensions.bech32ToHexOrNull
import at.torch.compose.ui.composable.navigation.routes.ChatRoomMessagingRoute
import at.torch.compose.ui.composable.navigation.routes.ImplementationPendingRoute
import at.torch.compose.ui.composable.navigation.routes.Route
import at.torch.compose.ui.theme.TorchTheme
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import fr.acinq.phoenix.utils.Parser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun StartDirectMessageToNpubOrNip05Dialog(
    activeUserPublicKey: HexKey,
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
                Spacer(
                    modifier = Modifier.height(4.dp)
                )
                Text(
                    modifier = Modifier.fillMaxWidth().padding(
                        horizontal = 20.dp,
                    ),
                    text = "Start chat via npub or nip05",
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center
                )
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
                            val input = textFieldState.text.toString()

                            val emailLikeAddress = Parser.parseEmailLikeAddress(input)

                            if (emailLikeAddress != null) {
                                // TODO: Get public Key from hex...
                                onNavigateToRoute.invoke(
                                    ImplementationPendingRoute(
                                        "Direct Message to ${textFieldState.text}"
                                    )
                                )
                            } else {
                                val publicKey = input.bech32ToHexOrNull()

                                if (publicKey != null) {
                                    onNavigateToRoute.invoke(
                                        ChatRoomMessagingRoute(
                                            activeUserPublicKey = activeUserPublicKey,
                                            chatRoomId = publicKey,
                                            relayHint = null
                                        )
                                    )
                                }
                            }

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

@Preview
@Composable
private fun StartDirectMessageToNpubOrNip05DialogPreview() {
    TorchTheme {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            StartDirectMessageToNpubOrNip05Dialog(
                activeUserPublicKey = "hex",
                scope = rememberCoroutineScope(),
                toggleOpenDialogSetting = {},
                onNavigateToRoute = {}
            )
        }
    }
}