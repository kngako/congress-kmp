package press.mantra.compose.database.model

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nipF4Podcasts.metadata.tags.AuthorTag
import com.vitorpamplona.quartz.utils.TimeUtils
import press.mantra.compose.database.model.traits.TimestampedEntity
import press.mantra.compose.nostr.nip30303.TranslationChapterProofReaderListEvent
import press.mantra.compose.nostr.nip30303.tags.TranslationChapterIdTag
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * 30309
 */
@Entity
data class MantraTranslationChapterProofReader(
    @PrimaryKey
    val id: HexKey,
    val publicKey: HexKey,
    val translationChapterId: HexKey,
    val proofReaderPublicKey: HexKey,

    val signature: String,

    override val createdAt: Instant = Clock.System.now(),
    override val updatedAt: Instant = createdAt
): TimestampedEntity {

    companion object {
        fun toTranslationChapterProofReaderListEvent(mantraTranslationChapterProofReaders: List<MantraTranslationChapterProofReader>): TranslationChapterProofReaderListEvent {
            // TODO: create TranslationChapterProofReaderListEvent from mantraTranslationChapterProofReaders
            return TranslationChapterProofReaderListEvent(
                id = "",
                pubKey = "",
                createdAt = TimeUtils.now(),
                tags = TagArrayBuilder<TranslationChapterProofReaderListEvent>()
                    .addUnique(
                        TranslationChapterIdTag.assemble("")
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

