package press.mantra.compose.database.model

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * 30302
 */
@Entity
data class MantraChapter(
    @PrimaryKey
    val id: HexKey,
    val artifactVersionId: HexKey,
    val publicKey: HexKey,
    val name: String,
    val originalText: String,
    val index: Int,
    val wordCount: Int,
    val characterCount: Int,

)
