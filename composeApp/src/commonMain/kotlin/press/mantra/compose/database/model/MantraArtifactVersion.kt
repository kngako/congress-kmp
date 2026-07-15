package press.mantra.compose.database.model

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * 30301
 */
@Entity
data class MantraArtifactVersion(
    @PrimaryKey
    val id: HexKey,
    val artifactId: HexKey,
    val publicKey: HexKey,

    /**
     * Version Tag...
     */
    val versionLabel: String,
)
