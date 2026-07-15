package press.mantra.compose.database.model

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * 30304
 */
@Entity
data class MantraDialect(
    @PrimaryKey
    val id: HexKey
)
