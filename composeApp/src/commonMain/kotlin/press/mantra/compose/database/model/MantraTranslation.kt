package press.mantra.compose.database.model

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.PrimaryKey
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import press.mantra.compose.database.model.traits.OptionalNostrEventEntity
import press.mantra.compose.database.model.traits.TimestampedEntity
import press.mantra.compose.nostr.nip30303.TranslationEvent
import press.mantra.compose.nostr.nip30303.tags.TranslationArtifactVersionIdTag
import press.mantra.compose.nostr.nip30303.tags.TranslationChunkIdTag
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * 30305
 */
@Entity(
    foreignKeys = [
        ForeignKey(
            entity = MantraTranslationChunk::class,
            parentColumns = ["id"],
            childColumns = ["translationChunkId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MantraTranslationArtifactVersion::class,
            parentColumns = ["id"],
            childColumns = ["translationArtifactVersionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class MantraTranslation(
    @PrimaryKey
    val id: HexKey,
    val publicKey: HexKey,
    val translationChunkId: HexKey,
    val translationArtifactVersionId: HexKey,
    val text: String,

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

    fun toTranslationEvent(): TranslationEvent {
        return TranslationEvent(
            id = id,
            pubKey = publicKey,
            createdAt = createdAt.epochSeconds,
            tags = TagArrayBuilder<TranslationEvent>()
                .addUnique(
                    TranslationArtifactVersionIdTag.assemble(translationArtifactVersionId)
                )
                .addUnique(
                    TranslationChunkIdTag.assemble(translationChunkId)
                )
                .build(),
            content = "",
            sig = signature
        )
    }
}