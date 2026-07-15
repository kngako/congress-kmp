package press.mantra.compose.database.model

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * 30311
 */
@Entity
data class MantraTranslationChunk(
    @PrimaryKey
    val id: HexKey,
    val text: String,
    val index: Int,
    val chunkId: HexKey,
    val translationChapterId: HexKey,
)
