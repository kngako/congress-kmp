package at.torch.compose.ui.composable

import androidx.compose.foundation.layout.Arrangement
import at.torch.compose.ui.composable.widgets.LoadingDataIndicator
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import at.torch.compose.repository.ChatRepository
import at.torch.compose.repository.NostrRepository
import at.torch.compose.ui.composable.navigation.routes.Route
import at.torch.compose.ui.view.model.ChatRoomCreationViewModel
import at.torch.compose.ui.view.state.form.TextField
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import fr.acinq.phoenix.data.ActiveWallet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun ChatRoomCreationScreen(
    activeWalletStateFlow: StateFlow<ActiveWallet?>,
    nostrRepository: NostrRepository,
    chatRepository: ChatRepository,
    onNavigateToChatRoom: (Route) -> Unit
) {
    val chatRoomCreationViewModel: ChatRoomCreationViewModel =  viewModel (
        factory = ChatRoomCreationViewModel.factory(
            activeWalletStateFlow = activeWalletStateFlow,
            nostrRepository = nostrRepository,
            chatRepository = chatRepository
        )
    )

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).fillMaxWidth()
        ) {

            Column(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TextField(
                    modifier = Modifier.fillMaxWidth(),
                    state = chatRoomCreationViewModel.chatRoomCreationFormState.nameField.textFieldState,
                    isError = chatRoomCreationViewModel.chatRoomCreationFormState.nameField.errorMessage.value != null,
                    supportingText = chatRoomCreationViewModel.chatRoomCreationFormState.nameField.errorMessage.value?.let {
                        {
                            Text(
                                text = it,
                                modifier = Modifier.padding(
                                    bottom = 8.dp
                                )
                            )
                        }
                    },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                    ),
                    label = {
                        Text(
                            text = "Name (eg. Alan Turin)",
                            maxLines = 1,
                        )
                    },
                    placeholder = {
                        Text(
                            text = "Enter the name you want to use for your profile",
                            maxLines = 1,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Badge,
                            contentDescription = "Name"
                        )
                    }
                )

                Text(
                    text = "This will be the display name for your profile and also important for search.",
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center
                )

                TextField(
                    modifier = Modifier.fillMaxWidth(),
                    state = chatRoomCreationViewModel.chatRoomCreationFormState.descriptionField.textFieldState,
                    isError = chatRoomCreationViewModel.chatRoomCreationFormState.descriptionField.errorMessage.value != null,
                    supportingText = chatRoomCreationViewModel.chatRoomCreationFormState.descriptionField.errorMessage.value?.let {
                        {
                            Text(
                                text = it,
                                modifier = Modifier.padding(
                                    bottom = 8.dp
                                )
                            )
                        }
                    },
                    lineLimits = TextFieldLineLimits.MultiLine(4),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                    ),
                    label = {
                        Text(
                            text = "Introduce yourself",
                            maxLines = 1,

                            )
                    },
                    placeholder = {
                        Text(
                            text = "What should people know about you?",
                            maxLines = 1,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = "Name"
                        )
                    }
                )

                Text(
                    text = "This will be shown when people open your profile.",
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center
                )


                Button(
                    onClick = {
                        chatRoomCreationViewModel.createChatRoom(
                            onNavigateToChatRoom =  {

                            }
                        )
                    }
                ) {
                    Text(
                        "Create chat"
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun LoadingScreenPreview() {
    _root_ide_package_.at.torch.compose.ui.theme.TorchTheme {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            ChatRoomCreationScreen(
                activeWalletStateFlow = MutableStateFlow(null),
                nostrRepository = NostrRepository.NO_OP_NOSTR_REPOSITORY,
                chatRepository = ChatRepository.NO_OP_CHAT_REPOSITORY,
                onNavigateToChatRoom = {}
            )
        }
    }
}