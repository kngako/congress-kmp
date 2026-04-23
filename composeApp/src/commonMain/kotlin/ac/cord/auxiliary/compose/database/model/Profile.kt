package ac.cord.auxiliary.compose.database.model

import ac.cord.auxiliary.compose.database.model.traits.BroadcastableEntity
import ac.cord.auxiliary.compose.database.model.traits.LocalStoreEntity
import ac.cord.auxiliary.compose.database.model.traits.NostrEventEntity
import ac.cord.auxiliary.compose.database.model.traits.SoftDeletableEntity
import ac.cord.auxiliary.compose.database.model.traits.TimestampedEntity
import ac.cord.auxiliary.compose.database.model.traits.UserViewableEntity
import ac.cord.auxiliary.compose.ui.composable.navigation.routes.NostrEventDetailRoute
import ac.cord.auxiliary.compose.ui.composable.navigation.routes.Route
import ac.cord.auxiliary.compose.ui.composable.widgets.profile.ProfileAvatar
import ac.cord.auxiliary.compose.ui.theme.AuxTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusModifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.time.Clock
import kotlin.time.Instant

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = NostrEvent::class,
            parentColumns = ["id"],
            childColumns = ["nostrEventId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index("nostrEventId"),
    ],
)

data class Profile(
    @PrimaryKey
    val publicKey: String,
    val userName: String? = null, // name in the nostr event...
    val displayName: String? = null,
    val picture: String? = null,
    val banner: String? = null,
    val website: String? = null,
    val about: String? = null,
    val bot: Boolean? = null,
    val pronouns: String? = null,
    val nip05: String? = null,
    val domain: String? = null,
    val lud06: String? = null,
    val lud16: String? = null,
    val twitter: String? = null,

    override val nostrEventId: String,

    override val createdAt: Instant = Clock.System.now(),
    override val updatedAt: Instant = Clock.System.now(),
    override val savedAt: Instant = Clock.System.now(),
    override val viewedAt: Instant? = null,
    override val deletedAt: Instant? = null,
    override val broadcastedAt: Instant? = null,
): NostrEventEntity, TimestampedEntity, LocalStoreEntity, UserViewableEntity, BroadcastableEntity, SoftDeletableEntity {
    fun humanReadableNameOrPubkey(): String {
        return displayName ?: userName ?: nip05 ?: twitter ?: publicKey
    }

    @Composable
    fun RenderAsListItem(
        onNavigateToEvent: (Route) -> Unit
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = 6.dp,
                vertical = 3.dp
            ),
            onClick = {
                onNavigateToEvent.invoke(
                    NostrEventDetailRoute(
                        nostrEventId
                    )
                )
            }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(
                    space = 10.dp,
                    alignment = Alignment.Start
                )
            ) {
                Column(
                    modifier = Modifier.padding(4.dp)
                ) {
                    ProfileAvatar(
                        publicKey = publicKey,
                        profile = this@Profile
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(
                        space = 4.dp,
                        alignment = Alignment.CenterVertically
                    )
                ) {
                    Text(
                        text = humanReadableNameOrPubkey(),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Text(
                        text = nip05 ?: "",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun ProfileRenderAsListItemPreview() {
    val profile = Profile(
        publicKey = "pubKey",
        displayName = "John Doe",
        nip05 = "john@doe.com",
        about = "I am the stone that the builder refused.",
        nostrEventId = "nostrEventId"
    )
    AuxTheme {
        Surface(
            modifier = Modifier.padding(20.dp)
        ) {
            profile.RenderAsListItem(
                onNavigateToEvent = {}
            )
        }
    }
}