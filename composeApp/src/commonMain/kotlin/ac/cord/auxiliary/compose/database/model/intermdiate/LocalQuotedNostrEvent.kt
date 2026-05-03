package ac.cord.auxiliary.compose.database.model.intermdiate

import ac.cord.auxiliary.compose.database.model.Mention
import ac.cord.auxiliary.compose.database.model.NostrEvent
import ac.cord.auxiliary.compose.database.model.Profile
import ac.cord.auxiliary.compose.database.model.QuotedRelation
import ac.cord.auxiliary.compose.extensions.toFormattedTimeAndDateString
import ac.cord.auxiliary.compose.ui.composable.widgets.content.RichContent
import ac.cord.auxiliary.compose.ui.composable.widgets.profile.ProfileAvatar
import ac.cord.auxiliary.compose.ui.composable.widgets.profile.ProfileColor
import ac.cord.auxiliary.compose.ui.theme.AuxTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.room3.Embedded
import androidx.room3.Relation
import com.vitorpamplona.quartz.nip01Core.core.HexKey

data class LocalQuotedNostrEvent(
    @Embedded val quotedRelation: QuotedRelation,

    @Relation(
        parentColumn = "quotedNostrEventId",
        entityColumn = "id"
    )
    val nostrEvent: NostrEvent,

    @Relation(
        parentColumn = "quotedProfilePublicKey",
        entityColumn = "publicKey"
    )
    val profile: Profile?,

    @Relation(
        entity = Mention::class,
        parentColumn = "quotedNostrEventId",
        entityColumn = "mentioningNostrEventId",
    )
    val mentionedProfiles: List<LocalMention> = emptyList(),
) {
    @Composable
    fun RenderNotePreview(
        onNavigateToEvent: (HexKey) -> Unit
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(10.dp)
//                    .clickable(
//                    onClick = {
//                        onNavigateToEvent.invoke(profile.nostrEventId)
//                    }
//                )
                ,
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
                        text = profile?.humanReadableNameOrPubkey() ?: nostrEvent.pubKey,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    profile?.staticIdentifier()?.let { staticIdentifier ->
                        Text(
                            text = staticIdentifier,
                            style = MaterialTheme.typography.bodySmall,
                            color = ProfileColor.fromProfile(profile)
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(
                    horizontal = 10.dp
                )
            ) {
                RichContent(
                    nostrEvent = nostrEvent,
                    localQuotedNostrEvent = null,
                    mentionedProfiles = mentionedProfiles,
                    onNavigateToEvent = onNavigateToEvent
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    text = nostrEvent.createdAt.toFormattedTimeAndDateString(),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Preview
@Composable
private fun LocalQuotedNostrEventPreview() {
    AuxTheme {
        Surface(
            modifier = Modifier.padding(20.dp)
        ) {
            LocalQuotedNostrEvent(
                nostrEvent = NostrEvent(
                    id = "1c7213118d4e17c6bad5c6b11125f005619bc3e43374affb9cd6483f665fd5e6",
                    content = "God is not in the habit of coming down from heaven to solve people’s problems on earth.",
                    pubKey = "e5e3ad0272d1fbeead8e439a4ad3453374ca4969c8d2be84025ba4ef8ae5a522",
                    kind = 0,
                    sig = "hexsig",
                    tags = emptyArray()
                ),
                quotedRelation = QuotedRelation(
                    quotingNostrEventId = "e5e3ad0272d1fbeead8e439a4ad3453374ca4969c8d2be84025ba4ef8ae5a520",
                    quotedProfilePublicKey = "e5e3ad0272d1fbeead8e439a4ad3453374ca4969c8d2be84025ba4ef8ae5a522",
                    quotedNostrEventId = "1c7213118d4e17c6bad5c6b11125f005619bc3e43374affb9cd6483f665fd5e6"
                ),
                profile = Profile(
                    publicKey = "e5e3ad0272d1fbeead8e439a4ad3453374ca4969c8d2be84025ba4ef8ae5a522",
                    nostrEventId = "referenced",
                    displayName = "Steve Biko"
                ),
            ).RenderNotePreview(
                onNavigateToEvent = {}
            )
        }
    }
}