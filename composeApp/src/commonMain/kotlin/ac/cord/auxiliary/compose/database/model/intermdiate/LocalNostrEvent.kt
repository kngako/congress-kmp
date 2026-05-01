package ac.cord.auxiliary.compose.database.model.intermdiate

import ac.cord.auxiliary.compose.database.model.InReplyToRelation
import ac.cord.auxiliary.compose.database.model.Mention
import ac.cord.auxiliary.compose.database.model.NostrEvent
import ac.cord.auxiliary.compose.database.model.Profile
import ac.cord.auxiliary.compose.database.model.QuotedRelation
import ac.cord.auxiliary.compose.database.model.RepostedRelation
import ac.cord.auxiliary.compose.extensions.toFormattedTimeAndDateString
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
import androidx.room.Embedded
import androidx.room.Ignore
import androidx.room.Junction
import androidx.room.Relation

data class LocalNostrEvent(
    @Embedded val nostrEvent: NostrEvent,

    @Relation(
        parentColumn = "pubKey",
        entityColumn = "publicKey"
    )
    val profile: Profile?,

    @Relation(
        parentColumn = "id",
        entityColumn = "repostingNostrEventId"
    )
    val repostedRelation: RepostedRelation? = null,

    @Relation(
        entity = QuotedRelation::class,
        parentColumn = "id",
        entityColumn = "quotingNostrEventId"
    )
    val localQuotedNostrEvent: LocalQuotedNostrEvent? = null,

//    @Relation(
//        parentColumn = "id",
//        entityColumn = "id",
//        associateBy = Junction(
//            value = QuotedRelation::class,
//            parentColumn = "quotingNostrEventId",
//            entityColumn = "quotedNostrEventId"
//        )
//    )
//    val quotedNostrEvent: NostrEvent? = null,

    @Relation(
        parentColumn = "id",
        entityColumn = "publicKey",
        associateBy = Junction(
            value = QuotedRelation::class,
            parentColumn = "quotingNostrEventId",
            entityColumn = "quotedProfilePublicKey"
        )
    )
    val quotedAuthorProfile: Profile? = null,

    @Relation(
        parentColumn = "id",
        entityColumn = "replyingNostrEventId"
    )
    val inReplyToNostrEvent: InReplyToRelation? = null,

    @Relation(
        entity = Mention::class,
        parentColumn = "id",
        entityColumn = "nostrEventId",
    )
    val mentionedProfiles: List<LocalMention?> = emptyList(),
) {
    @Composable
    fun RenderNotePreview() {
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
            Text(
                modifier = Modifier.fillMaxWidth().padding(
                    horizontal = 20.dp
                ),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Start,
                overflow = TextOverflow.Ellipsis,
                text = nostrEvent.content
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