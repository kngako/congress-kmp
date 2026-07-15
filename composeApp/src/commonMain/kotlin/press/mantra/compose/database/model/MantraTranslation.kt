package press.mantra.compose.database.model

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * 30305
 */
@Entity
data class MantraTranslation(
    @PrimaryKey
    val id: HexKey,
    val publicKey: HexKey,
    val translationChunkId: HexKey,
    val translationArtifactVersionId: HexKey,
    val text: String,
)
