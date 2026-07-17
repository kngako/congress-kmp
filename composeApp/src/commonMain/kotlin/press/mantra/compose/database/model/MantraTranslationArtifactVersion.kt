package press.mantra.compose.database.model

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import press.mantra.compose.database.model.traits.OptionalNostrEventEntity
import press.mantra.compose.database.model.traits.TimestampedEntity
import press.mantra.compose.nostr.nip30303.ArtifactVersionEvent
import press.mantra.compose.nostr.nip30303.TranslationArtifactVersionEvent
import press.mantra.compose.nostr.nip30303.tags.ArtifactVersionIdTag
import press.mantra.compose.nostr.nip30303.tags.DialectIdTag
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * 30306
 */
@Entity
data class MantraTranslationArtifactVersion(
    @PrimaryKey
    val id: HexKey,
    val publicKey: HexKey,
    val artifactVersionId: HexKey,
    val dialectId: HexKey,
    val name: String,
    val visibility: String,

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

    fun toTranslationArtifactVersionEvent(): TranslationArtifactVersionEvent {
        return TranslationArtifactVersionEvent(
            id = id,
            pubKey = publicKey,
            createdAt = createdAt.epochSeconds,
            tags = TagArrayBuilder<ArtifactVersionEvent>()
                .addUnique(
                    ArtifactVersionIdTag.assemble(artifactVersionId)
                )
                .addUnique(
                    DialectIdTag.assemble(dialectId)
                )
                .build(),
            content = "",
            sig = signature
        )
    }
}

