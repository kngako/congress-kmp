package at.torch.compose.database.model

import at.torch.compose.ui.composable.navigation.routes.NostrEventDetailRoute
import at.torch.compose.ui.composable.widgets.profile.ProfileAvatar
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip51Lists.relayLists.RelayFeedsListEvent
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
): at.torch.compose.database.model.traits.NostrEventEntity,
    at.torch.compose.database.model.traits.TimestampedEntity,
    at.torch.compose.database.model.traits.LocalStoreEntity,
    at.torch.compose.database.model.traits.UserViewableEntity,
    at.torch.compose.database.model.traits.BroadcastableEntity,
    at.torch.compose.database.model.traits.SoftDeletableEntity {
    fun humanReadableNameOrPubkey(): String {
        return if (!displayName.isNullOrBlank()) {
            displayName
        } else if (!userName.isNullOrBlank()) {
            userName
        } else if (!nip05.isNullOrBlank()) {
            nip05
        } else if (!lud16.isNullOrBlank()) {
            lud16
        } else if (!twitter.isNullOrBlank()) {
            twitter
        } else {
            publicKey.take(8)
        }
    }

    fun staticIdentifier(): String? {
        return  if (!nip05.isNullOrBlank()) {
            nip05
        } else if (!lud16.isNullOrBlank()) {
            lud16
        } else {
            null
        }
    }

    suspend fun profilePublicRelays(
        nostrRepository: at.torch.compose.repository.NostrRepository
    ): List<NormalizedRelayUrl>? {
        val relays = nostrRepository.getNostrEvent(
            publicKey,
            kind = RelayFeedsListEvent.KIND
        )?.let { relayFeedNostrEvent ->
            val relayFeedEvent = RelayFeedsListEvent(
                id = relayFeedNostrEvent.id,
                pubKey = relayFeedNostrEvent.pubKey,
                content = relayFeedNostrEvent.content,
                tags = relayFeedNostrEvent.tags,
                sig = relayFeedNostrEvent.sig,
                createdAt = relayFeedNostrEvent.createdAt.epochSeconds
            )

            relayFeedEvent.publicRelays()
        }

        if (relays?.isNotEmpty() == true) {
            return relays
        }

        return null
    }

    @Composable
    fun RenderAsListItem(
        activeUserPublicKey: String,
        onNavigateToEvent: (at.torch.compose.ui.composable.navigation.routes.Route) -> Unit
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = 6.dp,
                vertical = 3.dp
            ),
            onClick = {
                onNavigateToEvent.invoke(
                    _root_ide_package_.at.torch.compose.ui.composable.navigation.routes.NostrEventDetailRoute(
                        activeUserPublicKey = activeUserPublicKey,
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
                    _root_ide_package_.at.torch.compose.ui.composable.widgets.profile.ProfileAvatar(
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

                    staticIdentifier()?.let { staticIdentifier ->
                        Text(
                            text = staticIdentifier,
                            style = MaterialTheme.typography.labelMedium,
                            color = _root_ide_package_.at.torch.compose.ui.composable.widgets.profile.ProfileColor.fromPublicKey(publicKey)
                        )
                    }

                }
            }
        }
    }
}

@Preview
@Composable
fun ProfileRenderAsListItemPreview(
    activeUserPublicKey: String
) {
    val profile = Profile(
        publicKey = "pubKey",
        displayName = "John Doe",
        nip05 = "john@doe.com",
        about = "I am the stone that the builder refused.",
        nostrEventId = "nostrEventId"
    )
    _root_ide_package_.at.torch.compose.ui.theme.TorchTheme {
        Surface(
            modifier = Modifier.padding(20.dp)
        ) {
            profile.RenderAsListItem(
                activeUserPublicKey = "",
                onNavigateToEvent = {}
            )
        }
    }
}