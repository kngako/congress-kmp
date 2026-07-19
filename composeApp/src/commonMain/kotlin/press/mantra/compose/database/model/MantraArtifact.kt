package press.mantra.compose.database.model

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip31Alts.AltTag
import press.mantra.compose.database.model.traits.OptionalNostrEventEntity
import press.mantra.compose.database.model.traits.TimestampedEntity
import press.mantra.compose.nostr.nip30303.ArtifactEvent
import press.mantra.compose.nostr.nip30303.tags.ArtifactMetadataTag
import press.mantra.compose.nostr.nip30303.tags.DialectIdTag
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * 30300
 */
@Entity
data class MantraArtifact(
    @PrimaryKey
    val id: HexKey,
    val publicKey: String,
    val name: String,
    val url: String, // Hash this to get the dTag...
    val visibility: String, // public, private, permissioned (public, but only members can contribute)
    val dialectId: String,
    val license: String,
//    val artifactApprovalId: String,

    /**
     * Signature will be an empty string if this is a rumor
     */
    val signature: String,

    val relayHint: String? = null,

    val marmotGroupEventId: HexKey? = null,
    /**
     * Related nostrEventId if this is not a rumor...
      */
    override val nostrEventId: HexKey? = null,
    override val createdAt: Instant = Clock.System.now(),
    override val updatedAt: Instant = createdAt
): OptionalNostrEventEntity, TimestampedEntity {
    fun toArtifactEvent(): ArtifactEvent {
        return ArtifactEvent(
            id = id,
            pubKey = publicKey,
            createdAt = createdAt.epochSeconds,
            tags = TagArrayBuilder<ArtifactEvent>()
                .addUnique(
                    ArtifactMetadataTag.assemble(
                        url = url,
                        visibility = visibility,
                        license = license
                    )
                )
                .addUnique(
                    DialectIdTag.assemble(dialectId)
                )
                .addUnique(
                    AltTag.assemble(ArtifactEvent.ALT_DESCRIPTION)
                )
                .build(),
            content = name,
            sig = signature
        )
    }

    companion object {
        fun fromArtifactEvent(artifactEvent: ArtifactEvent): MantraArtifact? {
            return artifactEvent.artifactMetadata?.let { artifactMetadata ->
                artifactEvent.dialectId()?.let { dialectId ->
                    MantraArtifact(
                        id = artifactEvent.id,
                        publicKey = artifactEvent.pubKey,
                        dialectId = dialectId,
                        createdAt = Instant.fromEpochSeconds(artifactEvent.createdAt),
                        url = artifactMetadata.url,
                        license = artifactMetadata.license,
                        visibility = artifactMetadata.visibility,
                        name = artifactEvent.content,
                        signature = artifactEvent.sig,
                    )
                }
            }
        }
    }
}
