package press.mantra.compose.database.model

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * 30300
 */
@Entity
data class MantraArtifact(
    @PrimaryKey
    val id: HexKey,
    val publicKey: String,
    val url: String, // Hash this to get the dTag...
    val visibility: String, // public, private, permissioned (public, but only members can contribute)
    val dialectId: String,
    val copyrightOwner: String,
    val license: String,
//    val artifactApprovalId: String,

)
