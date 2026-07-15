package press.mantra.compose.database.model

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * 30313
 */
@Entity
data class MantraTranslationTranslator(
    @PrimaryKey
    val id: HexKey,
    val translationId: HexKey,
    val translatorPublicKey: HexKey,
    val content: String,
)
