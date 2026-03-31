package ac.aux.compose.ui.composable.widgets.feed

import ac.aux.compose.database.model.NostrEvent
import ac.aux.compose.database.model.Profile
import ac.aux.compose.database.model.Repost
import ac.aux.compose.database.model.intermdiate.LocalNostrEvent
import ac.aux.compose.extensions.toFormattedTimeAndDateString
import ac.aux.compose.ui.theme.AuxTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import kotlin.time.Instant

@Composable
fun LocalNostrEvent.ListView(
    onNavigateToEvent: (NostrEvent) -> Unit
) {
    when (nostrEvent.kind) {
        RepostEvent.KIND -> {
            // TODO: Make use of the indexed Repost field to render...

            Column(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = profile.displayName ?: profile.userName ?: profile.publicKey,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = "reposted",
                        style = MaterialTheme.typography.labelSmall)
                }

                if (taggedNostrEvent != null) {
                    TextNoteFeedItem(
                        nostrEvent = taggedNostrEvent,
                        profile = taggedAuthor,
                        onNavigateToEvent = onNavigateToEvent
                    )
                } else {

                    val containedPost = RepostEvent(
                        id = nostrEvent.id,
                        pubKey = nostrEvent.pubKey,
                        createdAt = nostrEvent.createdAt.epochSeconds,
                        content = nostrEvent.content,
                        sig = nostrEvent.sig,
                        tags = nostrEvent.tags
                    ).containedPost()

                    if (containedPost != null) {
                        Text("Loading author information...")
                        Text(
                            containedPost.content
                        )
                        Text(
                            text = Instant.fromEpochSeconds(containedPost.createdAt).toFormattedTimeAndDateString()
                        )
                    } else {
                        Text("Malformed Note")
                    }

                }
            }
        }
        // TODO: Support other kinds...
        else -> {
            TextNoteFeedItem(
                nostrEvent = nostrEvent,
                profile = profile,
                onNavigateToEvent = onNavigateToEvent
            )
        }
    }
}

@Composable
private fun TextNoteFeedItem(
    nostrEvent: NostrEvent,
    profile: Profile?,
    onNavigateToEvent: (NostrEvent) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.padding(
            horizontal = 10.dp
        ),
        onClick = {
            onNavigateToEvent.invoke(nostrEvent)
        }
    ) {
        Column(
            modifier = Modifier.padding(10.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.Start),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = "Profile picture",
                )
                Text(
                    modifier = Modifier.weight(1f),
                    text = profile?.displayName ?: profile?.userName ?: nostrEvent.pubKey,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                text = nostrEvent.content,
            )

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    10.dp,
                    alignment = Alignment.Start
                )
            ) {
                item {
                    AssistChip(
                        label = {
                            Text(
                                text = nostrEvent.kind.toString() // TODO: Kind by name...
                            )
                        },
                        onClick = {

                        }
                    )

                }
                items(
                    items = nostrEvent.tags,
                ) { tag ->
                    SuggestionChip(
                        label = {
                            Text(
                                text = tag.joinToString(":")
                            )
                        },
                        onClick = {

                        }
                    )
                }
            }

            Text(
                text = nostrEvent.createdAt.toFormattedTimeAndDateString()
            )
        }
    }
}

@Composable
private fun RepostFeedItem(
    repost: Repost,
    localNostrEvent: LocalNostrEvent,
    onNavigateToEvent: (NostrEvent) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.padding(
            horizontal = 10.dp
        ),
        onClick = {
            onNavigateToEvent.invoke(localNostrEvent.nostrEvent)
        }
    ) {
        Column(
            modifier = Modifier.padding(10.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.Start),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = "Profile picture",
                )
                Text(
                    modifier = Modifier.weight(1f),
                    text = repost.repostedPostAuthorPublicKey,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                text = repost.content,
            )

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    10.dp,
                    alignment = Alignment.Start
                )
            ) {
                item {
                    AssistChip(
                        label = {
                            Text(
                                text = localNostrEvent.nostrEvent.kind.toString() // TODO: Kind by name...
                            )
                        },
                        onClick = {

                        }
                    )

                }
                items(
                    items = localNostrEvent.nostrEvent.tags,
                ) { tag ->
                    SuggestionChip(
                        label = {
                            Text(
                                text = tag.joinToString(":")
                            )
                        },
                        onClick = {

                        }
                    )
                }
            }

            Text(
                text = localNostrEvent.nostrEvent.createdAt.toFormattedTimeAndDateString()
            )
        }
    }
}

@Preview
@Composable
private fun EventListViewPreview() {
    AuxTheme {
        Surface(
            modifier = Modifier.padding(20.dp)
        ) {
            LocalNostrEvent(
                nostrEvent = NostrEvent(
                    id = "hex",
                    content = "Something something seomthing",
                    pubKey = "hexpubkeypubkeypubkeypubkey",
                    kind = 0,
                    sig = "hexsig",
                    tags = emptyArray()
                ),
                profile = Profile(
                    publicKey = "",
                    nostrEventId = "referenced",
                    displayName = "Frank Talk"
                ),
            ).ListView(
                onNavigateToEvent = {}
            )
        }
    }
}