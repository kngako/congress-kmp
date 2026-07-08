package at.torch.compose.database.model

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.PrimaryKey
import at.torch.compose.database.model.traits.BroadcastableEntity
import at.torch.compose.database.model.traits.LocalStoreEntity
import at.torch.compose.database.model.traits.NostrEventEntity
import at.torch.compose.database.model.traits.SoftDeletableEntity
import at.torch.compose.database.model.traits.TimestampedEntity
import at.torch.compose.exceptions.MarmotMissingRetainedExporterSecretsException
import at.torch.compose.extensions.exporterSecret
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEvent
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEventEncryption
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroup
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.Log
import kotlin.time.Clock
import kotlin.time.Instant

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = NostrEvent::class,
            parentColumns = ["id"],
            childColumns = ["nostrEventId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class MarmotGroupEvent(
    @PrimaryKey
    val id: HexKey,

    /**
     * Sender Public Key
     */
    val userPublicKey: String,

    val publicKey: HexKey,

    val chatRoomId: String,

    val signature: String, // TODO: Add signature...

    val encryptedContent: String,
    val expiresAt: Instant?,

    /**
     * What gets broadcasts?
     */
    override val nostrEventId: String,

    override val createdAt: Instant = Clock.System.now(),
    override val updatedAt: Instant = createdAt,
    override val savedAt: Instant = Clock.System.now(),
    override val deletedAt: Instant? = null,
    override val broadcastedAt: Instant? = null,
) : NostrEventEntity, TimestampedEntity, LocalStoreEntity, BroadcastableEntity, SoftDeletableEntity {

    companion object {
        const val KIND = GroupEvent.KIND
    }

}