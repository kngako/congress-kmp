package press.mantra.compose.database.model

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * 30309
 */
@Entity
data class MantraTranslationChapterProofReader(
    @PrimaryKey
    val id: HexKey,
    val translationChapterId: HexKey,
    val proofReaderPublicKey: HexKey,
)
