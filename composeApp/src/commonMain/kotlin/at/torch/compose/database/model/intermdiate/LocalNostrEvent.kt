package at.torch.compose.database.model.intermdiate

import at.torch.compose.ui.composable.widgets.content.RichContent
import at.torch.compose.ui.composable.widgets.profile.ProfileAvatar
import at.torch.compose.extensions.toFormattedTimeAndDateString
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
import androidx.compose.ui.unit.dp
import androidx.room3.Embedded
import androidx.room3.Relation
import com.vitorpamplona.quartz.nip01Core.core.HexKey

data class LocalNostrEvent(
    @Embedded val nostrEvent: at.torch.compose.database.model.NostrEvent,

    @Relation(
        parentColumns = ["pubKey"],
        entityColumns = ["publicKey"]
    )
    val profile: at.torch.compose.database.model.Profile?,

    @Relation(
        entity = _root_ide_package_.at.torch.compose.database.model.RepostedRelation::class,
        parentColumns = ["id"],
        entityColumns = ["repostingNostrEventId"]
    )
    val localRepostedNostrEvent: LocalRepostedNostrEvent? = null,

    @Relation(
        entity = _root_ide_package_.at.torch.compose.database.model.QuotedRelation::class,
        parentColumns = ["id"],
        entityColumns = ["quotingNostrEventId"]
    )
    val localQuotedNostrEvent: LocalQuotedNostrEvent? = null,

    @Relation(
        entity = _root_ide_package_.at.torch.compose.database.model.InReplyToRelation::class,
        parentColumns = ["id"],
        entityColumns = ["replyingNostrEventId"]
    )
    val localInReplyToNostrEvent: LocalInReplyToNostrEvent? = null,

    @Relation(
        entity = _root_ide_package_.at.torch.compose.database.model.Mention::class,
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

                _root_ide_package_.ac.cord.compose.ui.composable.widgets.profile.ProfileAvatar(
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
            _root_ide_package_.ac.cord.compose.ui.composable.widgets.content.RichContent(
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