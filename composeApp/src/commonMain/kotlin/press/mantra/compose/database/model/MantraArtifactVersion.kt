package press.mantra.compose.database.model

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import press.mantra.compose.database.model.traits.TimestampedEntity
import press.mantra.compose.nostr.nip30303.ArtifactVersionEvent
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

    override val createdAt: Instant = Clock.System.now(),
    override val updatedAt: Instant = createdAt
): TimestampedEntity {
    fun toArtifactVersionEvent(): ArtifactVersionEvent {
        return ArtifactVersionEvent(
            id = "",
            pubKey = publicKey,
            createdAt = createdAt.epochSeconds,
            tags = emptyArray(),
            content = "",
            sig = ""
        )
    }
}
