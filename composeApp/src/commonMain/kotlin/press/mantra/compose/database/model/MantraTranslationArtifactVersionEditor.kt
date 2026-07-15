package press.mantra.compose.database.model

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.TimeUtils
import press.mantra.compose.database.model.traits.TimestampedEntity
import press.mantra.compose.nostr.nip30303.TranslationArtifactVersionEditorListEvent
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * 30307
 */
@Entity
data class MantraTranslationArtifactVersionEditor(
    @PrimaryKey
    val id: HexKey,
    val publicKey: HexKey,
    val translationArtifactVersionId: HexKey,
    val editorPublicKey: HexKey,

    val signature: String,

    override val createdAt: Instant = Clock.System.now(),
    override val updatedAt: Instant = createdAt
): TimestampedEntity {

    companion object {
        fun toTranslationChapterProofReaderListEvent(mantraTranslationChapterProofReaders: List<MantraTranslationChapterProofReader>): TranslationArtifactVersionEditorListEvent {
            // TODO: create TranslationChapterProofReaderListEvent from mantraTranslationChapterProofReaders
            return TranslationArtifactVersionEditorListEvent(
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

