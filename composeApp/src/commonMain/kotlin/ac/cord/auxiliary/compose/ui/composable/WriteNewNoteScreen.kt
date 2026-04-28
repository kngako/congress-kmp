package ac.cord.auxiliary.compose.ui.composable

import ac.cord.auxiliary.compose.database.model.NostrEvent
import ac.cord.auxiliary.compose.database.model.Profile
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalNostrEvent
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalProfileWithFollowing
import ac.cord.auxiliary.compose.extensions.toFormattedTimeAndDateString
import ac.cord.auxiliary.compose.repository.NostrRepository
import ac.cord.auxiliary.compose.ui.composable.widgets.LoadingDataIndicator
import ac.cord.auxiliary.compose.ui.composable.widgets.detail.TextNoteEventDetail
import ac.cord.auxiliary.compose.ui.composable.widgets.profile.ProfileAvatar
import ac.cord.auxiliary.compose.ui.theme.AuxTheme
import ac.cord.auxiliary.compose.ui.view.model.WriteNewNoteViewModel
import ac.cord.auxiliary.compose.ui.view.state.WriteNewNoteUIState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WriteNewNoteScreen(
    initialWriteNewNoteUIState: WriteNewNoteUIState = WriteNewNoteUIState.Loading,
    replyToNostrEventId: HexKey?,
    quotedNostrEventId: HexKey?,
    onNostrEventPublished: () -> Unit,
    nostrRepository: NostrRepository
) {
    val writeNewNoteViewModel: WriteNewNoteViewModel =  viewModel (
        factory = WriteNewNoteViewModel.factory(
            replyToNostrEventId = replyToNostrEventId,
            quotedNostrEventId = quotedNostrEventId,
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
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                when (val writeNewNoteUIState = writeNewNoteViewModel.writeNewNoteUIState) {
                    is WriteNewNoteUIState.Loading -> {
                        LoadingDataIndicator()
                    }
                    is WriteNewNoteUIState.InputPrompt -> {

                        val listState = rememberLazyListState()

                        LazyColumn(
                            state = listState,
                            modifier = Modifier.weight(1f)
                        ) {
                            // TODO: Stick Header with post button...

                            item {
                                if (writeNewNoteUIState.inReplyToNostrEvent?.nostrEvent?.kind == TextNoteEvent.KIND) {
                                    writeNewNoteUIState.inReplyToNostrEvent.RenderNotePreview()
                                } else {
                                    Spacer(
                                        modifier = Modifier.height(1.dp)
                                    )
                                }
                            }

                            item {
                                Column(
                                    modifier = Modifier.fillParentMaxHeight()
                                ) {
                                    if (writeNewNoteUIState.inReplyToNostrEvent != null) {
                                        Text(
                                            modifier = Modifier.padding(
                                                start = 50.dp,
                                                bottom = 10.dp,

                                            ),
                                            text = "replying To ${writeNewNoteUIState.inReplyToNostrEvent.profile?.humanReadableNameOrPubkey() ?: "note"}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

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
                                                text = if (writeNewNoteUIState.inReplyToNostrEvent != null) {
                                                    "What's your reply to the above"
                                                } else if (writeNewNoteUIState.quotedNostrEvent != null) {
                                                    "What's your comment on the below"
                                                } else {
                                                    "What vibrations do you want to send out?"
                                                },
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

                                    if (writeNewNoteUIState.quotedNostrEvent?.nostrEvent?.kind == TextNoteEvent.KIND) {
                                        Card(
                                            modifier = Modifier.padding(
                                                start = 50.dp,
                                                top = 10.dp
                                            )
                                        ) {
                                            writeNewNoteUIState.quotedNostrEvent.RenderNotePreview()
                                        }
                                    }
                                }


                            }
                        }

                        if (writeNewNoteUIState.inReplyToNostrEvent != null) {
                            LaunchedEffect(writeNewNoteUIState) {
                                listState.scrollToItem(1)
                            }
                        }


                        Button(
                            onClick = {
                                writeNewNoteViewModel.confirmNote(
                                    localProfile = writeNewNoteUIState.localProfile,
                                    inReplyToNostrEvent = writeNewNoteUIState.inReplyToNostrEvent,
                                    quotedNostrEvent = writeNewNoteUIState.quotedNostrEvent
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

                        val listState = rememberLazyListState()

                        LazyColumn(
                            state = listState,
                            modifier = Modifier.weight(1f)
                        ) {
                            item {
                                if (writeNewNoteUIState.inReplyToNostrEvent?.nostrEvent?.kind == TextNoteEvent.KIND) {

                                    Card {
                                        writeNewNoteUIState.inReplyToNostrEvent.RenderNotePreview()
                                    }


                                    HorizontalDivider(
                                        modifier = Modifier.padding(20.dp)
                                    )
                                } else {
                                    Spacer(
                                        modifier = Modifier.height(1.dp)
                                    )
                                }
                            }

                            item {
                                Column(
                                    modifier = Modifier.fillParentMaxHeight().fillMaxWidth().padding(
                                        10.dp
                                    ),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = writeNewNoteUIState.localProfile.profile.humanReadableNameOrPubkey(),
                                        style = MaterialTheme.typography.bodyLarge
                                    )

                                    writeNewNoteUIState.localProfile.profile.staticIdentifier()?.let { staticIdentifier ->
                                        Text(
                                            text = staticIdentifier,
                                            style = MaterialTheme.typography.labelSmallEmphasized
                                        )
                                    }

                                    ProfileAvatar(
                                        profile = writeNewNoteUIState.localProfile.profile
                                    )

                                    if (writeNewNoteUIState.inReplyToNostrEvent != null) {
                                        Text(
                                            text = "replying To ${writeNewNoteUIState.inReplyToNostrEvent.profile?.humanReadableNameOrPubkey() ?: "note"}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Text(
                                        text = writeNewNoteUIState.text,
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center
                                    )

                                    if (writeNewNoteUIState.quotedNostrEvent?.nostrEvent?.kind == TextNoteEvent.KIND) {
                                        Card {
                                            writeNewNoteUIState.quotedNostrEvent.RenderNotePreview()
                                        }

                                    }

                                    Text(
                                        text = Clock.System.now().toFormattedTimeAndDateString(),
                                        style = MaterialTheme.typography.labelSmall

                                    )
                                }
                            }
                        }

                        if (writeNewNoteUIState.inReplyToNostrEvent != null) {
                            LaunchedEffect(writeNewNoteUIState) {
                                listState.scrollToItem(1)
                            }
                        }

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
                                        onNostrEventPublished = onNostrEventPublished,
                                        inReplyToNostrEvent = writeNewNoteUIState.inReplyToNostrEvent,
                                        quotedNostrEvent = writeNewNoteUIState.quotedNostrEvent
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
                replyToNostrEventId = null,
                quotedNostrEventId = null,
                initialWriteNewNoteUIState =
                    WriteNewNoteUIState.InputPrompt(
//                        text = "Yes, I did it. So WHAT?!",
                        quotedNostrEvent = LocalNostrEvent(
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
                        ),
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
                                nip05 = "john@doe.com",
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