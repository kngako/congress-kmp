package press.mantra.compose.database.model

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import press.mantra.compose.database.model.traits.OptionalNostrEventEntity
import press.mantra.compose.database.model.traits.TimestampedEntity
import press.mantra.compose.nostr.nip30303.ArtifactVersionEvent
import press.mantra.compose.nostr.nip30303.tags.ArtifactIdTag
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * 30301
 */
@Entity
data class MantraArtifactVersion(
    @PrimaryKey
    val id: HexKey,
    val artifactId: HexKey,
    val publicKey: HexKey,

    /**
     * Version Tag...
     */
    val versionLabel: String,

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
    fun toArtifactVersionEvent(): ArtifactVersionEvent {
        return ArtifactVersionEvent(
            id = id,
            pubKey = publicKey,
            createdAt = createdAt.epochSeconds,
            tags = TagArrayBuilder<ArtifactVersionEvent>().addUnique(
                ArtifactIdTag.assemble(artifactId)
            ).build(),
            content = "",
            sig = signature
        )
    }
}
