package press.mantra.compose.database.model

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.PrimaryKey
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import press.mantra.compose.database.model.traits.OptionalNostrEventEntity
import press.mantra.compose.database.model.traits.TimestampedEntity
import press.mantra.compose.nostr.nip30303.ArtifactVersionEvent
import press.mantra.compose.nostr.nip30303.TranslationArtifactVersionEvent
import press.mantra.compose.nostr.nip30303.tags.ArtifactVersionIdTag
import press.mantra.compose.nostr.nip30303.tags.DialectIdTag
import press.mantra.compose.nostr.nip30303.tags.TranslationArtifactVersionMetadataTag
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * 30306
 */
@Entity(
    foreignKeys = [
        ForeignKey(
            entity = MantraArtifactVersion::class,
            parentColumns = ["id"],
            childColumns = ["artifactVersionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MantraDialect::class,
            parentColumns = ["id"],
            childColumns = ["dialectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class MantraTranslationArtifactVersion(
    @PrimaryKey
    val id: HexKey,
    val publicKey: HexKey,
    val artifactVersionId: HexKey,
    val dialectId: HexKey,
    val name: String,
    val visibility: String,
    val license: String,

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
                .addUnique(
                    TranslationArtifactVersionMetadataTag.assemble(
                        name = name,
                        visibility = visibility,
                        license = license
                    )
                )
                .build(),
            content = "",
            sig = signature
        )
    }

    companion object {
        fun fromTranslationArtifactVersionEvent(translationArtifactVersionEvent: TranslationArtifactVersionEvent): MantraTranslationArtifactVersion? {

            return translationArtifactVersionEvent.artifactVersionId()?.let { artifactVersionId ->
                translationArtifactVersionEvent.dialectId()?.let { dialectId ->
                    translationArtifactVersionEvent.translationArtifactVersionMetadata?.let { translationArtifactVersionMetadata ->
                        MantraTranslationArtifactVersion(
                            id = translationArtifactVersionEvent.id,
                            publicKey = translationArtifactVersionEvent.pubKey,
                            createdAt = Instant.fromEpochSeconds(translationArtifactVersionEvent.createdAt),
                            artifactVersionId = artifactVersionId,
                            dialectId = dialectId,
                            name = translationArtifactVersionMetadata.name,
                            visibility = translationArtifactVersionMetadata.visibility,
                            license = translationArtifactVersionMetadata.license,
                            signature = translationArtifactVersionEvent.sig
                        )
                    }
                }
            }
        }
    }
}

