package ac.cord.auxiliary.compose.ui.composable.widgets.feed

import ac.cord.auxiliary.compose.database.model.NostrEvent
import ac.cord.auxiliary.compose.database.model.Profile
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalInReplyToNostrEvent
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalMention
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalQuotedNostrEvent
import ac.cord.auxiliary.compose.extensions.toFormattedTimeAndDateString
import ac.cord.auxiliary.compose.ui.composable.widgets.content.RichContent
import ac.cord.auxiliary.compose.ui.composable.widgets.profile.ProfileAvatar
import ac.cord.auxiliary.compose.ui.composable.widgets.profile.ProfileColor
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vitorpamplona.quartz.nip01Core.core.HexKey

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TextNoteFeedItem(
    nostrEvent: NostrEvent,
    profile: Profile?,
    localInReplyToNostrEvent: LocalInReplyToNostrEvent?,
    localQuotedNostrEvent: LocalQuotedNostrEvent?,
    mentionedProfiles: List<LocalMention>,
    onNavigateToEvent: (HexKey) -> Unit
) {
    if (localInReplyToNostrEvent != null) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = 20.dp
            ),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "In reply to ",
                style = MaterialTheme.typography.labelSmall
            )

            Text(
                modifier = Modifier.clickable(
                    onClick = {
                        onNavigateToEvent.invoke(localInReplyToNostrEvent.inReplyToRelation.inReplyToNostrEventId)
                    }
                ),
                style = MaterialTheme.typography.labelLargeEmphasized,
                text = localInReplyToNostrEvent.profile?.humanReadableNameOrPubkey() ?: "loading information..."
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

                    Column(
                        modifier = Modifier.weight(1f)
                    ){
                        Text(
                            text = profile.humanReadableNameOrPubkey(),
                            style = MaterialTheme.typography.bodyMedium
                        )

                        profile.staticIdentifier()?.let { staticIdentifier ->
                            Text(
                                text = staticIdentifier,
                                style = MaterialTheme.typography.bodySmall,
                                color = ProfileColor.fromProfile(profile)
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "Loading author information"
                )
            }

            RichContent(
                modifier = Modifier.fillMaxWidth(),
                nostrEvent = nostrEvent,
                localQuotedNostrEvent = localQuotedNostrEvent,
                mentionedProfiles = mentionedProfiles
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

//                item {
//                    SuggestionChip(
//                        label = {
//                            Text(
//                                text = if (localNostrEvent.mentionedProfiles.isNotEmpty()) {
//                                    localNostrEvent.mentionedProfiles.joinToString(",") { it?.profile?.humanReadableNameOrPubkey() ?: "Find user information" }
//                                } else {
//                                    ":-("
//                                }
//                            )
//                        },
//                        onClick = {
//
//                        }
//                    )
//                }

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
