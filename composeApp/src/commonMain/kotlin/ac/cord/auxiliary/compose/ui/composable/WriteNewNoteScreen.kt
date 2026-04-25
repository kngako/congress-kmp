package ac.cord.auxiliary.compose.ui.composable

import ac.cord.auxiliary.compose.database.model.NostrEvent
import ac.cord.auxiliary.compose.database.model.Profile
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalProfileWithFollowing
import ac.cord.auxiliary.compose.repository.NostrRepository
import ac.cord.auxiliary.compose.ui.composable.widgets.LoadingDataIndicator
import ac.cord.auxiliary.compose.ui.composable.widgets.profile.ProfileAvatar
import ac.cord.auxiliary.compose.ui.theme.AuxTheme
import ac.cord.auxiliary.compose.ui.view.model.WriteNewNoteViewModel
import ac.cord.auxiliary.compose.ui.view.state.WriteNewNoteUIState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WriteNewNoteScreen(
    initialWriteNewNoteUIState: WriteNewNoteUIState = WriteNewNoteUIState.Loading,
    onNostrEventPublished: () -> Unit,
    nostrRepository: NostrRepository
) {
    val writeNewNoteViewModel: WriteNewNoteViewModel =  viewModel (
        factory = WriteNewNoteViewModel.factory(
            initialWriteNewNoteUIState,
            nostrRepository
        )
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

                when (val writeNewNoteUIState = writeNewNoteViewModel.writeNewNoteUIState) {
                    is WriteNewNoteUIState.Loading -> {
                        LoadingDataIndicator()
                    }
                    is WriteNewNoteUIState.InputPrompt -> {
                        Text(
                            "Write new note",
                            style = MaterialTheme.typography.headlineSmall
                        )

                        TextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = writeNewNoteViewModel.writeNewNoteFormState.textField.text.value,
                            onValueChange = {
                                writeNewNoteViewModel.writeNewNoteFormState.textField.text.value = it
                            },
                            isError = writeNewNoteViewModel.writeNewNoteFormState.textField.errorMessage.value != null,
                            supportingText = writeNewNoteViewModel.writeNewNoteFormState.textField.errorMessage.value?.let {
                                {
                                    Text(
                                        text = it,
                                        modifier = Modifier.padding(
                                            bottom = 8.dp
                                        )
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                            ),
                            label = {
                                Text(
                                    text = "What vibrations do you want to send out?",
                                    maxLines = 1,
                                )
                            },
                            placeholder = {
                                Text(
                                    text = "Type out what you would like to publish",
                                    maxLines = 1,
                                )
                            },
                            leadingIcon = {
                                ProfileAvatar(
                                    profile = writeNewNoteUIState.localProfile.profile,
                                    size = 35.dp
                                )
                            }
                        )

                        Button(
                            onClick = {
                                writeNewNoteViewModel.confirmNote(
                                    localProfile = writeNewNoteUIState.localProfile
                                )
                            },
                        ) {
                            Text(
                                "Next"
                            )
                            Icon(
                                Icons.Default.NavigateNext,
                                contentDescription = "Next"
                            )
                        }

                    }
                    is WriteNewNoteUIState.ConfirmNote-> {

                        // TODO: Get profile for active pubKey
                        Text(
                            text = "Display Name",
                            style = MaterialTheme.typography.labelLarge
                        )

                        Text(
                            text = writeNewNoteUIState.text,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )


                        Spacer(
                            modifier = Modifier.weight(1f)
                        )



                        Text(
                            text = "The above will be your new note.",
                            style = MaterialTheme.typography.bodySmall
                        )

                        if (writeNewNoteViewModel.isActionPending.value) {
                            LoadingIndicator(
                                modifier = Modifier
                            )
                        } else {
                            Button(
                                onClick = {
                                    writeNewNoteViewModel.createNewNote(
                                        onNostrEventPublished = onNostrEventPublished
                                    )
                                }
                            ) {
                                Text(
                                    text = "Transmit Note"
                                )
                            }
                        }

                    }
                    is WriteNewNoteUIState.Error -> {
                        Spacer(
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Something went wrong and we were unable to write a new note. Please try again later.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        Spacer(
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    key(true) {
        writeNewNoteViewModel.initiate()
    }
}

@Preview
@Composable
private fun WriteNewNoteScreenPreview() {
    AuxTheme {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            WriteNewNoteScreen(
                initialWriteNewNoteUIState =
                    WriteNewNoteUIState.InputPrompt(
                        localProfile = LocalProfileWithFollowing(
                            nostrEvent = NostrEvent(
                                id = "eventId",
                                pubKey = "publicKey",
                                content = """Lorem Ipsum is simply dummy text of the printing and typesetting industry. 
                                
Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown printer took a galley of type and scrambled it to make a type specimen book. 
                                
It has survived not only five centuries, but also the leap into electronic typesetting, remaining essentially unchanged. It was popularised in the 1960s with the release of Letraset sheets containing Lorem Ipsum passages, and more recently with desktop publishing software like Aldus PageMaker including versions of Lorem Ipsum.""".trimIndent(),
                                tags = emptyArray(),
                                sig = "",
                                kind = TextNoteEvent.KIND
                            ),
                            profile = Profile(
                                publicKey = "pubKey",
                                displayName = "John Doe",
                                nostrEventId = "nostrEventId"
                            ),
                            following = emptyList()
                        )
                    ),
                onNostrEventPublished = {},
                nostrRepository = NostrRepository.NO_OP_NOSTR_REPOSITORY)
        }
    }
}