package ac.aux.compose.ui.composable

import ac.aux.compose.database.model.Profile
import ac.aux.compose.repository.NostrRepository
import ac.aux.compose.ui.theme.AuxTheme
import ac.aux.compose.ui.theme.BluePill
import ac.aux.compose.ui.theme.RedPill
import ac.aux.compose.ui.view.model.WriteNewNoteViewModel
import ac.aux.compose.ui.view.state.CreateProfileUIState
import ac.aux.compose.ui.view.state.WriteNewNoteUIState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.NextPlan
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WriteNewNoteScreen(
    initialWriteNewNoteUIState: WriteNewNoteUIState = WriteNewNoteUIState.InputPrompt,
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

                when (val writeNewNoteUIState = writeNewNoteViewModel.writeNewNoteUIState.value) {
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
                                    text = "Enter the name you want to use for your profile",
                                    maxLines = 1,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Create,
                                    contentDescription = "Write note"
                                )
                            }
                        )

                        Button(
                            onClick = {

                                writeNewNoteViewModel.validateInput().let { isInputValid ->
                                    if (isInputValid) {
                                        writeNewNoteViewModel.writeNewNoteUIState.value =
                                            WriteNewNoteUIState.ConfirmNote(
                                                text = writeNewNoteViewModel.writeNewNoteFormState.textField.text.value,
                                            )
                                    }
                                }
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
                    WriteNewNoteUIState.InputPrompt,
//                    CreateProfileUIState.Error,
//                    CreateProfileUIState.ConfirmInput(
//                        name = "Alan Turing",
//                        bio = "Polyglot: Math... is my middle name, computer scientist. cryptographer, and gold hider."
//                    ),
//                    CreateProfileUIState.UnsignedProfile(
//                        unsignedNostrEvent = UnsignedNostrEvent(
//                            kind = 0,
//                            content = "Something here",
//                            tags = emptyArray(),
//                        ),
//                        name = "Alan Turing",
//                        bio = "Polyglot: Math... is my middle name, computer scientist. cryptographer, and gold hider."
//                    ),
//                    CreateProfileUIState.UnbroadcastedProfile(
//                        nostrEvent = NostrEvent(
//                            id = "",
//                            pubKey = "",
//                            kind = 0,
//                            content = "Something here",
//                            tags = emptyArray(),
//                            sig = ""
//                        ),
//                        name = "Alan Turing",
//                        bio = "Polyglot: Math... is my middle name, computer scientist. cryptographer, and gold hider."
//                    ),
                onNostrEventPublished = {},
                nostrRepository = NostrRepository.NO_OP_NOSTR_REPOSITORY)
        }
    }
}