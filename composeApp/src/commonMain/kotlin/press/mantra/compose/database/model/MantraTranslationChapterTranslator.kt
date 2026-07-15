package press.mantra.compose.database.model

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.TimeUtils
import press.mantra.compose.database.model.traits.TimestampedEntity
import press.mantra.compose.nostr.nip30303.TranslationChapterTranslatorListEvent
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * 30310
 */
@Entity
data class MantraTranslationChapterTranslator(
    @PrimaryKey
    val id: HexKey,
    val publicKey: HexKey,
    val translationChapterId: HexKey,
    val translatorPublicKey: HexKey,

    override val createdAt: Instant = Clock.System.now(),
    override val updatedAt: Instant = createdAt
): TimestampedEntity {

    companion object {
        fun toTranslationChapterTranslatorListEvent(mantraTranslationChapterTranslators: List<MantraTranslationChapterTranslator>): TranslationChapterTranslatorListEvent {
            // TODO: create TranslationChapterProofReaderListEvent from mantraTranslationChapterProofReaders
            return TranslationChapterTranslatorListEvent(
                id = "",
                pubKey = "",
                createdAt = TimeUtils.now(),
                tags = emptyArray(),
                content = "",
                sig = ""
            )
        }
    }
}

