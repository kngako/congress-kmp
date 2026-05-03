package ac.cord.auxiliary.compose.ui.composable.widgets.feed

import ac.cord.auxiliary.compose.database.model.InReplyToRelation
import ac.cord.auxiliary.compose.database.model.NostrEvent
import ac.cord.auxiliary.compose.database.model.Profile
import ac.cord.auxiliary.compose.database.model.RepostedRelation
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalInReplyToNostrEvent
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalNostrEvent
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalRepostedNostrEvent
import ac.cord.auxiliary.compose.extensions.toFormattedTimeAndDateString
import ac.cord.auxiliary.compose.ui.composable.widgets.content.RichContent
import ac.cord.auxiliary.compose.ui.composable.widgets.profile.ProfileAvatar
import ac.cord.auxiliary.compose.ui.composable.widgets.profile.ProfileColor
import ac.cord.auxiliary.compose.ui.theme.AuxTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
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
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import kotlin.time.Instant

@Composable
fun LocalNostrEvent.ListView(
    onNavigateToEvent: (HexKey) -> Unit
) {
    when (nostrEvent.kind) {
        RepostEvent.KIND -> {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 10.dp
                        ).clickable(
                            enabled = true,
                            onClick = {
                                onNavigateToEvent.invoke(profile?.nostrEventId ?: nostrEvent.id)
                            }
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        modifier = Modifier.size(
                            22.dp
                        ),
                        imageVector = Icons.Default.Repeat,
                        contentDescription = "repost icon",

                    )
                    Text(
                        text = profile?.humanReadableNameOrPubkey() ?: nostrEvent.pubKey,
                        style = MaterialTheme.typography.labelSmall,
                        color = ProfileColor.fromPublicKey(profile?.publicKey ?: nostrEvent.pubKey)
                    )
                    Text(
                        text = "reposted",
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                if (localRepostedNostrEvent != null) {
                    TextNoteFeedItem(
                        nostrEvent = localRepostedNostrEvent.nostrEvent,
                        profile = localRepostedNostrEvent.profile,
                        localQuotedNostrEvent = null,
                        localInReplyToNostrEvent = null,
                        mentionedProfiles = localRepostedNostrEvent.mentionedProfiles,
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
                localQuotedNostrEvent = localQuotedNostrEvent,
                localInReplyToNostrEvent = localInReplyToNostrEvent,
                mentionedProfiles = mentionedProfiles,
                onNavigateToEvent = onNavigateToEvent
            )
        }
    }
}


@Preview
@Composable
private fun EventListViewRepostedPreview() {
    AuxTheme {
        Surface(
            modifier = Modifier.padding(20.dp)
        ) {
            LocalNostrEvent(
                nostrEvent = NostrEvent(
                    id = "hex",
                    content = "Reposting",
                    pubKey = "hexpubkeypubkeypubkeypubkey",
                    kind = RepostEvent.KIND,
                    sig = "hexsig",
                    tags = emptyArray()
                ),
                localRepostedNostrEvent = LocalRepostedNostrEvent(
                    repostedRelation = RepostedRelation(
                        repostingNostrEventId = "hex",
                        repostedNostrEventId = "repostedhex",
                        repostedProfilePublicKey = "reposterHex"
                    ),
                    nostrEvent = NostrEvent(
                        id = "hex",
                        content = "Something something something",
                        pubKey = "hexpubkeypubkeypubkeypubkey",
                        kind = 0,
                        sig = "hexsig",
                        tags = emptyArray()
                    ),
                    profile = Profile(
                        publicKey = "hesd",
                        nostrEventId = "referenced",
                        displayName = "Frank Talk",
                        nip05 = "frank@talk.at"
                    ),
                    mentionedProfiles = emptyList()
                ),
                profile = Profile(
                    publicKey = "",
                    nostrEventId = "referenced",
                    displayName = "Steve Biko",
                    nip05 = "steve@bck.co.za"
                ),
            ).ListView(
                onNavigateToEvent = {}
            )
        }
    }
}

@Preview
@Composable
private fun EventListViewInReplyPreview() {
    AuxTheme {
        Surface(
            modifier = Modifier.padding(20.dp)
        ) {
            LocalNostrEvent(
                nostrEvent = NostrEvent(
                    id = "hex",
                    content = "Reposting",
                    pubKey = "hexpubkeypubkeypubkeypubkey",
                    kind = TextNoteEvent.KIND,
                    sig = "hexsig",
                    tags = emptyArray()
                ),
                localInReplyToNostrEvent = LocalInReplyToNostrEvent(
                    inReplyToRelation = InReplyToRelation(
                        inReplyToNostrEventId = "nostrEvent",
                        inReplyToRootNostrEventId = "rootId",
                        inReplyToProfilePublicKey = "inReplyToProfileKey",
                        inReplyToRootProfilePublicKey = "rootPubKey",
                        replyingNostrEventId = "hex"
                    ),
                    nostrEvent = NostrEvent(
                        id = "hex",
                        content = "Something something something",
                        pubKey = "hexpubkeypubkeypubkeypubkey",
                        kind = 0,
                        sig = "hexsig",
                        tags = emptyArray()
                    ),
                    profile = Profile(
                        publicKey = "hesd",
                        nostrEventId = "referenced",
                        displayName = "Frank Talk",
                        nip05 = "frank@talk.at"
                    ),
                    mentionedProfiles = emptyList()
                ),
                profile = Profile(
                    publicKey = "",
                    nostrEventId = "referenced",
                    displayName = "Steve Biko",
                    nip05 = "steve@bck.co.za"
                ),
            ).ListView(
                onNavigateToEvent = {}
            )
        }
    }
}