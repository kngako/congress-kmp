package at.torch.compose.ui.composable.widgets.feed

import at.torch.compose.database.model.InReplyToRelation
import at.torch.compose.database.model.Mention
import at.torch.compose.database.model.NostrEvent
import at.torch.compose.database.model.Profile
import at.torch.compose.database.model.QuotedRelation
import at.torch.compose.database.model.intermdiate.LocalInReplyToNostrEvent
import at.torch.compose.database.model.intermdiate.LocalMention
import at.torch.compose.database.model.intermdiate.LocalQuotedNostrEvent
import at.torch.compose.ui.composable.widgets.content.RichContent
import at.torch.compose.ui.composable.widgets.profile.ProfileAvatar
import at.torch.compose.extensions.toFormattedTimeAndDateString
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.quartz.nip01Core.core.HexKey

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TextNoteFeedItem(
    nostrEvent: at.torch.compose.database.model.NostrEvent,
    profile: at.torch.compose.database.model.Profile?,
    localInReplyToNostrEvent: at.torch.compose.database.model.intermdiate.LocalInReplyToNostrEvent?,
    localQuotedNostrEvent: at.torch.compose.database.model.intermdiate.LocalQuotedNostrEvent?,
    mentionedProfiles: List<at.torch.compose.database.model.intermdiate.LocalMention>,
    onNavigateToEvent: (HexKey) -> Unit
) {
    val expended = mutableStateOf(false)

    Column {

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
                    color = _root_ide_package_.at.torch.compose.ui.composable.widgets.profile.ProfileColor.fromPublicKey(localInReplyToNostrEvent.nostrEvent.pubKey),
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
                        _root_ide_package_.at.torch.compose.ui.composable.widgets.profile.ProfileAvatar(
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
                                    color = _root_ide_package_.at.torch.compose.ui.composable.widgets.profile.ProfileColor.fromProfile(profile)
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Loading author information"
                    )
                }

                _root_ide_package_.at.torch.compose.ui.composable.widgets.content.RichContent(
                    modifier = Modifier.fillMaxWidth(),
                    nostrEvent = nostrEvent,
                    localQuotedNostrEvent = localQuotedNostrEvent,
                    mentionedProfiles = mentionedProfiles,
                    onNavigateToEvent = onNavigateToEvent
                )

                if (expended.value) {
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
                                        text = tag.joinToString(":"),
                                        maxLines = 1
                                    )
                                },
                                onClick = {

                                }
                            )
                        }
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

}

@Preview
@Composable
private fun TextNoteFeedItemPreview() {
    _root_ide_package_.at.torch.compose.ui.theme.TorchTheme {
        Surface(
            modifier = Modifier.padding(20.dp)
        ) {
            TextNoteFeedItem(
                nostrEvent = _root_ide_package_.at.torch.compose.database.model.NostrEvent(
                    id = "hex",
                    content = "You are either alive and proud or you are dead, and when you are dead, you can't care anyway. https://www.brainyquote.com/authors/steven-biko-quotes",
                    pubKey = "hexpubkeypubkeypubkeypubkey",
                    kind = 0,
                    sig = "hexsig",
                    tags = emptyArray()
                ),
                profile = _root_ide_package_.at.torch.compose.database.model.Profile(
                    publicKey = "",
                    nostrEventId = "referenced",
                    displayName = "Frank Talk",
                    nip05 = "frank@talk.at"
                ),
                localQuotedNostrEvent = null,
                localInReplyToNostrEvent = null,
                mentionedProfiles = emptyList(),
                onNavigateToEvent = {}
            )
        }
    }
}

@Preview
@Composable
private fun TextNoteFeedItemInReplyToPreview() {
    _root_ide_package_.at.torch.compose.ui.theme.TorchTheme {
        Surface(
            modifier = Modifier.padding(20.dp)
        ) {
            TextNoteFeedItem(
                nostrEvent = _root_ide_package_.at.torch.compose.database.model.NostrEvent(
                    id = "hex",
                    content = "It is better to die for an idea that will live, than to live for an idea that will die",
                    pubKey = "hexpubkeypubkeypubkeypubkey",
                    kind = 0,
                    sig = "hexsig",
                    tags = emptyArray()
                ),
                profile = _root_ide_package_.at.torch.compose.database.model.Profile(
                    publicKey = "",
                    nostrEventId = "referenced",
                    displayName = "Frank Talk",
                    nip05 = "frank@talk.at"
                ),
                localQuotedNostrEvent = null,
                localInReplyToNostrEvent = _root_ide_package_.at.torch.compose.database.model.intermdiate.LocalInReplyToNostrEvent(
                    inReplyToRelation = _root_ide_package_.at.torch.compose.database.model.InReplyToRelation(
                        inReplyToNostrEventId = "nostrEvent",
                        inReplyToRootNostrEventId = "rootId",
                        inReplyToProfilePublicKey = "inReplyToProfileKey",
                        inReplyToRootProfilePublicKey = "rootPubKey",
                        replyingNostrEventId = "hex"
                    ),
                    nostrEvent = _root_ide_package_.at.torch.compose.database.model.NostrEvent(
                        id = "hex",
                        content = "Something something something",
                        pubKey = "hexie",
                        kind = 0,
                        sig = "hexsig",
                        tags = emptyArray()
                    ),
                    profile = _root_ide_package_.at.torch.compose.database.model.Profile(
                        publicKey = "hesd",
                        nostrEventId = "referenced",
                        displayName = "Steve Biko",
                        nip05 = "steve@bcm.co.za"
                    ),
                    mentionedProfiles = emptyList()
                ),
                mentionedProfiles = emptyList(),
                onNavigateToEvent = {}
            )
        }
    }
}

@Preview
@Composable
private fun TextNoteFeedItemQuoteNostrEventPreview() {
    _root_ide_package_.at.torch.compose.ui.theme.TorchTheme {
        Surface(
            modifier = Modifier.padding(20.dp)
        ) {
            TextNoteFeedItem(
                nostrEvent = _root_ide_package_.at.torch.compose.database.model.NostrEvent(
                    id = "hex",
                    content = "The revolutionary sees his task as liberation not only of the oppressed but also of the oppressor. Happiness can never truly exist in a state of tension.\n" +
                            "Read more at nostr:npub1uh366qnj68a7atvwgwdy4569xd6v5jtferftapqztwjwlzh9553q47pqsx\nnostr:nevent1qqspcusnzxx5u97xht2udvg3yhcq2cvmc0jrxa90lwwdvjplve0atespzpmhxue69uhkummnw3ezuamfdejsyg8h5g639qhuv803hzwexfwhyud6t6suxfu364anddqvrmn9j4cahvxksr8q",
                    pubKey = "hexpubkeypubkeypubkeypubkey",
                    kind = 0,
                    sig = "hexsig",
                    tags = emptyArray()
                ),
                profile = _root_ide_package_.at.torch.compose.database.model.Profile(
                    publicKey = "",
                    nostrEventId = "referenced",
                    displayName = "Frank Talk",
                    nip05 = "frank@talk.at"
                ),
                localQuotedNostrEvent = _root_ide_package_.at.torch.compose.database.model.intermdiate.LocalQuotedNostrEvent(
                    nostrEvent = _root_ide_package_.at.torch.compose.database.model.NostrEvent(
                        id = "1c7213118d4e17c6bad5c6b11125f005619bc3e43374affb9cd6483f665fd5e6",
                        content = "God is not in the habit of coming down from heaven to solve people’s problems on earth.",
                        pubKey = "e5e3ad0272d1fbeead8e439a4ad3453374ca4969c8d2be84025ba4ef8ae5a522",
                        kind = 0,
                        sig = "hexsig",
                        tags = emptyArray()
                    ),
                    quotedRelation = _root_ide_package_.at.torch.compose.database.model.QuotedRelation(
                        quotingNostrEventId = "e5e3ad0272d1fbeead8e439a4ad3453374ca4969c8d2be84025ba4ef8ae5a520",
                        quotedProfilePublicKey = "e5e3ad0272d1fbeead8e439a4ad3453374ca4969c8d2be84025ba4ef8ae5a522",
                        quotedNostrEventId = "1c7213118d4e17c6bad5c6b11125f005619bc3e43374affb9cd6483f665fd5e6"
                    ),
                    profile = _root_ide_package_.at.torch.compose.database.model.Profile(
                        publicKey = "e5e3ad0272d1fbeead8e439a4ad3453374ca4969c8d2be84025ba4ef8ae5a522",
                        nostrEventId = "referenced",
                        displayName = "Steve Biko"
                    ),
                ),
                localInReplyToNostrEvent = _root_ide_package_.at.torch.compose.database.model.intermdiate.LocalInReplyToNostrEvent(
                    inReplyToRelation = _root_ide_package_.at.torch.compose.database.model.InReplyToRelation(
                        inReplyToNostrEventId = "nostrEvent",
                        inReplyToRootNostrEventId = "rootId",
                        inReplyToProfilePublicKey = "inReplyToProfileKey",
                        inReplyToRootProfilePublicKey = "rootPubKey",
                        replyingNostrEventId = "hex"
                    ),
                    nostrEvent = _root_ide_package_.at.torch.compose.database.model.NostrEvent(
                        id = "hex",
                        content = "Something something something",
                        pubKey = "hexie",
                        kind = 0,
                        sig = "hexsig",
                        tags = emptyArray()
                    ),
                    profile = _root_ide_package_.at.torch.compose.database.model.Profile(
                        publicKey = "hesd",
                        nostrEventId = "referenced",
                        displayName = "Steve Biko",
                        nip05 = "steve@bcm.co.za"
                    ),
                    mentionedProfiles = emptyList()
                ),
                mentionedProfiles = listOf(
                    _root_ide_package_.at.torch.compose.database.model.intermdiate.LocalMention(
                        mention = _root_ide_package_.at.torch.compose.database.model.Mention(
                            mentioningNostrEventId = "000067b3f15cac88ba066b28c370083a4fc80dacc054e554ab1dc0def974c89c",
                            mentionedPublicKey = "e5e3ad0272d1fbeead8e439a4ad3453374ca4969c8d2be84025ba4ef8ae5a522"
                        ),
                        profile = _root_ide_package_.at.torch.compose.database.model.Profile(
                            publicKey = "e5e3ad0272d1fbeead8e439a4ad3453374ca4969c8d2be84025ba4ef8ae5a522",
                            nostrEventId = "referenced",
                            displayName = "Steve Biko",
                            nip05 = "steve@bcm.co.za"
                        ),
                    )
                ),
                onNavigateToEvent = {}
            )
        }
    }
}