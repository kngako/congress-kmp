package ac.cord.auxiliary.compose.database.model.intermdiate

import ac.cord.auxiliary.compose.database.model.InReplyToRelation
import ac.cord.auxiliary.compose.database.model.Mention
import ac.cord.auxiliary.compose.database.model.NostrEvent
import ac.cord.auxiliary.compose.database.model.Profile
import ac.cord.auxiliary.compose.database.model.QuotedRelation
import ac.cord.auxiliary.compose.database.model.RepostedRelation
import ac.cord.auxiliary.compose.extensions.toFormattedTimeAndDateString
import ac.cord.auxiliary.compose.ui.composable.widgets.content.RichContent
import ac.cord.auxiliary.compose.ui.composable.widgets.profile.ProfileAvatar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.room3.Embedded
import androidx.room3.Ignore
import androidx.room3.Junction
import androidx.room3.Relation
import com.vitorpamplona.quartz.nip01Core.core.HexKey

data class LocalNostrEvent(
    @Embedded val nostrEvent: NostrEvent,

    @Relation(
        parentColumns = ["pubKey"],
        entityColumns = ["publicKey"]
    )
    val profile: Profile?,

    @Relation(
        entity = RepostedRelation::class,
        parentColumns = ["id"],
        entityColumns = ["repostingNostrEventId"]
    )
    val localRepostedNostrEvent: LocalRepostedNostrEvent? = null,

    @Relation(
        entity = QuotedRelation::class,
        parentColumns = ["id"],
        entityColumns = ["quotingNostrEventId"]
    )
    val localQuotedNostrEvent: LocalQuotedNostrEvent? = null,

    @Relation(
        entity = InReplyToRelation::class,
        parentColumns = ["id"],
        entityColumns = ["replyingNostrEventId"]
    )
    val localInReplyToNostrEvent: LocalInReplyToNostrEvent? = null,

    @Relation(
        entity = Mention::class,
        parentColumns = ["id"],
        entityColumns = ["mentioningNostrEventId"],
    )
    val mentionedProfiles: List<LocalMention> = emptyList(),
) {
    @Composable
    fun RenderNotePreview(
        onNavigateToEvent: (HexKey) -> Unit
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val profile = profile

                ProfileAvatar(
                    profile = profile,
                    publicKey = nostrEvent.pubKey
                )

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = profile?.displayName ?: profile?.userName ?: nostrEvent.pubKey,
                        style = MaterialTheme.typography.labelLarge
                    )

                    profile?.nip05?.let {
                        Text(
                            text = profile.nip05 ?: "",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
            RichContent(
                modifier = Modifier.fillMaxWidth().padding(
                    horizontal = 20.dp
                ),
                style = MaterialTheme.typography.labelSmall,
                nostrEvent = nostrEvent,
                localQuotedNostrEvent = localQuotedNostrEvent,
                mentionedProfiles = mentionedProfiles,
                onNavigateToEvent = onNavigateToEvent
            )
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