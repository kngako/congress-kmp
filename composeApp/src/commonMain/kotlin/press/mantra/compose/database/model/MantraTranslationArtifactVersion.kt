package press.mantra.compose.database.model

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import press.mantra.compose.database.model.traits.TimestampedEntity
import press.mantra.compose.nostr.nip30303.TranslationArtifactVersionEvent
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

    val signature: String,

    override val createdAt: Instant = Clock.System.now(),
    override val updatedAt: Instant = createdAt
): TimestampedEntity {

    fun toTranslationArtifactVersionEvent(): TranslationArtifactVersionEvent {
        return TranslationArtifactVersionEvent(
            id = id,
            pubKey = publicKey,
            createdAt = createdAt.epochSeconds,
            tags = emptyArray(),
            content = "",
            sig = signature
        )
    }
}

