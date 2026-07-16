package press.mantra.compose.database.model

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nipF4Podcasts.metadata.tags.AuthorTag
import com.vitorpamplona.quartz.utils.TimeUtils
import press.mantra.compose.database.model.traits.TimestampedEntity
import press.mantra.compose.nostr.nip30303.TranslationTranslatorListEvent
import press.mantra.compose.nostr.nip30303.tags.TranslationIdTag
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * 30312
 */
@Entity
data class MantraTranslationTranslator(
    @PrimaryKey
    val id: HexKey,
    val publicKey: HexKey,
    val translationId: HexKey,
    val translatorPublicKey: HexKey,

    val signature: String,

    override val createdAt: Instant = Clock.System.now(),
    override val updatedAt: Instant = createdAt
): TimestampedEntity {

    companion object {
        fun toTranslationChapterProofReaderListEvent(mantraTranslationChapterProofReaders: List<MantraTranslationChapterProofReader>): TranslationTranslatorListEvent {
            // TODO: create TranslationChapterProofReaderListEvent from mantraTranslationChapterProofReaders
            return TranslationTranslatorListEvent(
                id = "",
                pubKey = "",
                createdAt = TimeUtils.now(),
                tags = TagArrayBuilder<TranslationTranslatorListEvent>()
                    .addUnique(
                        TranslationIdTag.assemble("") // TODO: Use actual translationId
                    )
                    .addUnique(
                        AuthorTag.assemble("") // TODO: PublicKeyListTag
                    )
                    .build(),
                content = "",
                sig = ""
            )
        }
    }
}

