package press.mantra.compose.database.model

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import press.mantra.compose.database.model.traits.OptionalNostrEventEntity
import press.mantra.compose.database.model.traits.TimestampedEntity
import press.mantra.compose.nostr.nip30303.ArtifactEvent
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
    val url: String, // Hash this to get the dTag...
    val visibility: String, // public, private, permissioned (public, but only members can contribute)
    val dialectId: String,
    val copyrightOwner: String,
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
                    DialectIdTag.assemble(dialectId)
                )
                .build(),
            content = "",
            sig = signature
        )
    }
}
