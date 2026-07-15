package press.mantra.compose.database.model

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * 30307
 */
@Entity
data class MantraTranslationArtifactVersionEditor(
    @PrimaryKey
    val id: HexKey,
    val translationArtifactVersionId: HexKey,
    val editorPublicKey: HexKey,
)
