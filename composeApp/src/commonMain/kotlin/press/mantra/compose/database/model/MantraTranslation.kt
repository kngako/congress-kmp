package press.mantra.compose.database.model

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import press.mantra.compose.database.model.traits.TimestampedEntity
import press.mantra.compose.nostr.nip30303.TranslationEvent
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * 30305
 */
@Entity
data class MantraTranslation(
    @PrimaryKey
    val id: HexKey,
    val publicKey: HexKey,
    val translationChunkId: HexKey,
    val translationArtifactVersionId: HexKey,
    val text: String,

    override val createdAt: Instant = Clock.System.now(),
    override val updatedAt: Instant = createdAt
): TimestampedEntity {

    fun toTranslationEvent(): TranslationEvent {
        return TranslationEvent(
            id = "",
            pubKey = publicKey,
            createdAt = createdAt.epochSeconds,
            tags = emptyArray(),
            content = "",
            sig = ""
        )
    }
}