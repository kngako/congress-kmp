package press.mantra.compose.database.model

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.PrimaryKey
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.utils.TimeUtils
import press.mantra.compose.database.model.traits.OptionalNostrEventEntity
import press.mantra.compose.database.model.traits.TimestampedEntity
import press.mantra.compose.nostr.nip30303.TranslationArtifactVersionContributorListEvent
import press.mantra.compose.nostr.nip30303.tags.ContributorTag
import press.mantra.compose.nostr.nip30303.tags.TranslationArtifactVersionIdTag
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * 30307
 */
@Entity(
    foreignKeys = [
        ForeignKey(
            entity = MantraTranslationArtifactVersionContributor::class,
            parentColumns = ["id"],
            childColumns = ["translationArtifactVersionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class MantraTranslationArtifactVersionContributor(
    @PrimaryKey
    val id: HexKey,
    val publicKey: HexKey,
    val translationArtifactVersionId: HexKey,
    val editorPublicKey: HexKey,

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

    companion object {
        fun toTranslationChapterProofReaderListEvent(mantraTranslationChapterProofReaders: List<MantraTranslationArtifactVersionContributor>): TranslationArtifactVersionContributorListEvent {
            // TODO: create TranslationChapterProofReaderListEvent from mantraTranslationChapterProofReaders
            return TranslationArtifactVersionContributorListEvent(
                id = "",
                pubKey = "",
                createdAt = TimeUtils.now(),
                tags = TagArrayBuilder<TranslationArtifactVersionContributorListEvent>()

                    .addUnique(
                        TranslationArtifactVersionIdTag.assemble("") // TODO: use actual value..
                    )
                    // TODO: Add tag for editorPublicKey
                    .addUnique(
                        ContributorTag.assemble(
                            "",
                            ContributorTag.ROLE_EDITOR
                        )
                    )
                    .build(),
                content = "",
                sig = ""
            )
        }
    }
}

