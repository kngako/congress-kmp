package ac.cord.auxiliary.compose.ui.composable.widgets.detail

import ac.cord.auxiliary.compose.database.model.NostrEvent
import ac.cord.auxiliary.compose.database.model.Profile
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalNostrEvent
import ac.cord.auxiliary.compose.extensions.toFormattedTimeAndDateString
import ac.cord.auxiliary.compose.ui.composable.widgets.profile.ProfileAvatar
import ac.cord.auxiliary.compose.ui.theme.AuxTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddReaction
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextNoteEventDetail(
    localNostrEvent: LocalNostrEvent,
    onNavigateBack: () -> Unit,
    onNavigateToWriteAReply: (String) -> Unit,
    onNavigateToQuoteNostrEvent: (String) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack
                    ) {
                        Icon(
                            Icons.Default.ArrowBackIosNew,
                            contentDescription = "Navigate back"
                        )
                    }
                },
                title = {
                    Text("Post")
                },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).fillMaxSize()
        ) {

            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val profile = localNostrEvent.profile

                            ProfileAvatar(
                                profile = localNostrEvent.profile,
                                publicKey = localNostrEvent.nostrEvent.pubKey
                            )

                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = profile?.displayName ?: profile?.userName ?: localNostrEvent.nostrEvent.pubKey,
                                    style = MaterialTheme.typography.bodyLarge
                                )

                                profile?.nip05?.let {
                                    Text(
                                        text = profile.nip05 ?: "",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }


                            Button(
                                onClick = {

                                }
                            ) {
                                Text(
                                    "Follow"
                                )
                            }

                            IconButton(
                                onClick = {
                                    // TODO: Show dropdown of more actions...
                                }
                            ) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "More options"
                                )
                            }
                        }
                        Text(
                            modifier = Modifier.fillMaxWidth().padding(
                                horizontal = 20.dp
                            ),
                            textAlign = TextAlign.Start,
                            text = localNostrEvent.nostrEvent.content
                        )



                        Row(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Text(
                                text = localNostrEvent.nostrEvent.createdAt.toFormattedTimeAndDateString()
                            )
                        }

                        HorizontalDivider()

                        BottomAppBar(
                            actions = {


                                IconButton(
                                    onClick = {

                                    }
                                ) {
                                    Icon(
                                        Icons.Default.AddReaction,
                                        contentDescription = "Add Reaction"
                                    )
                                }

                                IconButton(
                                    onClick = {

                                    }
                                ) {
                                    Icon(
                                        Icons.Default.CurrencyBitcoin,
                                        contentDescription = "Tip"
                                    )
                                }


                                IconButton(
                                    onClick = {
                                        onNavigateToQuoteNostrEvent.invoke(
                                            localNostrEvent.nostrEvent.id
                                        )
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Repeat,
                                        contentDescription = "Repost"
                                    )
                                }
                            },
                            floatingActionButton = {
                                ExtendedFloatingActionButton(
                                    onClick = {
                                        onNavigateToWriteAReply.invoke(
                                            localNostrEvent.nostrEvent.id
                                        )
                                    },
                                    icon = {
                                        Icon(
                                            Icons.Default.ChatBubble,
                                            contentDescription = "Reply"
                                        )
                                    },
                                    text = {
                                        Text("Reply")
                                    }
                                )
                            },
                            windowInsets = WindowInsets.navigationBars
                        )

                        HorizontalDivider()

                        Text("Replies coming soon...")
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun TextNoteEventDetailPreview() {

    AuxTheme {
        Surface(
            modifier = Modifier.padding(20.dp)
        ) {
            TextNoteEventDetail(
                localNostrEvent = LocalNostrEvent(
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
                    )
                ),
                onNavigateBack = {},
                onNavigateToWriteAReply = {},
                onNavigateToQuoteNostrEvent = {}
            )
        }
    }
}