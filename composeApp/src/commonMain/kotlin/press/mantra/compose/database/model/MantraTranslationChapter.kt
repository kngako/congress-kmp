package press.mantra.compose.database.model

import androidx.room3.Entity
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * 30308
 */
@Entity
data class MantraTranslationChapter(
    val id: HexKey,
    val chapterId: HexKey,
    val translationArtifactVersionId: HexKey,
    val index: Int
)
