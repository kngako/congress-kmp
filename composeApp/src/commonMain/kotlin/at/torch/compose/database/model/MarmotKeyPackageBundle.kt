package at.torch.compose.database.model

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import at.torch.compose.database.model.traits.BroadcastableEntity
import at.torch.compose.database.model.traits.LocalStoreEntity
import at.torch.compose.database.model.traits.SoftDeletableEntity
import at.torch.compose.database.model.traits.TimestampedEntity
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlin.time.Clock
import kotlin.time.Instant

@Entity(
//    foreignKeys = [
//        ForeignKey(
//            entity = Profile::class,
//            parentColumns = ["publicKey"],
//            childColumns = ["publicKey"],
//            onDelete = ForeignKey.CASCADE,
//        ),
//    ],
    indices = [
        Index("publicKey"),
    ],
)
data class MarmotKeyPackageBundle(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val publicKey: HexKey,
    // TODO: have the keyPackageEventId

    val tlsEncodedMarmotKeyPackage: String,
    val ncryptsecInitPrivateKey: String,
    val ncryptsecEncryptionPrivateKey: String,
    val ncryptsecSignaturePrivateKey: String,

    override val createdAt: Instant = Clock.System.now(),
    override val updatedAt: Instant = Clock.System.now(),
    override val savedAt: Instant = Clock.System.now(),
    override val deletedAt: Instant? = null,
    override val broadcastedAt: Instant? = null,
): TimestampedEntity, LocalStoreEntity, BroadcastableEntity, SoftDeletableEntity