package ac.cord.auxiliary.compose.ui.composable.widgets.feed

import ac.cord.auxiliary.compose.database.model.NostrEvent
import ac.cord.auxiliary.compose.database.model.Profile
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalNostrEvent
import ac.cord.auxiliary.compose.extensions.toFormattedTimeAndDateString
import ac.cord.auxiliary.compose.ui.composable.widgets.profile.ProfileAvatar
import ac.cord.auxiliary.compose.ui.theme.AuxTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import kotlin.time.Instant

@Composable
fun LocalNostrEvent.ListView(
    onNavigateToEvent: (HexKey) -> Unit
) {
    when (nostrEvent.kind) {
        RepostEvent.KIND -> {
            Column(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(
                        enabled = true,
                        onClick = {
                            onNavigateToEvent.invoke(profile?.nostrEventId ?: nostrEvent.id)
                            // TODO: Show error
                        }
                    )
                ) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = profile?.displayName ?: profile?.userName ?: nostrEvent.pubKey,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = "reposted",
                        style = MaterialTheme.typography.labelSmall)
                }

                if (repostedRelation != null) {
                    TextNoteFeedItem(
                        localNostrEvent = this@ListView,
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
                localNostrEvent = this,
                onNavigateToEvent = onNavigateToEvent
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TextNoteFeedItem(
    localNostrEvent: LocalNostrEvent,
    onNavigateToEvent: (HexKey) -> Unit
) {
    val nostrEvent = localNostrEvent.nostrEvent
    if (localNostrEvent.repostedRelation != null) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = 20.dp
            ),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // TODO: Get a nested repost... localNostrEvent...
            Text(
                "In reply to ",
                style = MaterialTheme.typography.labelSmall
            )

            Text(
                modifier = Modifier.clickable(
                    onClick = {
                        onNavigateToEvent.invoke(localNostrEvent.repostedRelation.repostedNostrEventId)
                    }
                ),
                style = MaterialTheme.typography.labelLargeEmphasized,
                text = localNostrEvent.repostedRelation.repostedProfilePublicKey ?: "loading information..."
            )
        }
    }

    ElevatedCard(
        modifier = Modifier.padding(
            horizontal = 10.dp
        ),
        onClick = {
            onNavigateToEvent.invoke(nostrEvent.id)
        }
    ) {
        Column(
            modifier = Modifier.padding(10.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val profile = localNostrEvent.profile
            if (profile != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(
                        onClick = {
                            onNavigateToEvent.invoke(profile.nostrEventId)
                        }
                    ),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.Start),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProfileAvatar(
                        publicKey = nostrEvent.pubKey,
                        profile = profile
                    )

                    Text(
                        modifier = Modifier.weight(1f),
                        text = profile.displayName ?: profile.userName ?: nostrEvent.pubKey,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                Text(
                    text = "Loading author information"
                )
            }

            // TODO: RichContent here...
//            RichContent(
//                modifier = Modifier.fillMaxWidth(),
//                localNostrEvent = localNostrEvent,
//            )
            Text(
                text = localNostrEvent.nostrEvent.content
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

                item {
                    SuggestionChip(
                        label = {
                            Text(
                                text = if (localNostrEvent.mentionedProfiles.isNotEmpty()) {
                                    localNostrEvent.mentionedProfiles.joinToString(",") { it.profile?.humanReadableNameOrPubkey() ?: "Find user information" }
                                } else {
                                    ":-("
                                }
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
                                text = tag.joinToString(":"),
                                maxLines = 1
                            )
                        },
                        onClick = {

                        }
                    )
                }
            }

            Text(
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.End,
                text = nostrEvent.createdAt.toFormattedTimeAndDateString()
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