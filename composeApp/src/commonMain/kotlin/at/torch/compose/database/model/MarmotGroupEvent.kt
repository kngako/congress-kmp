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

//    val signature: String, // TODO: Add signature...

    val encryptedContent: String,
    val expiresAt: Instant? = null,

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

        fun fromGroupEvent(
            groupEvent: GroupEvent,
            userPublicKey: String
        ): MarmotGroupEvent? {
            return groupEvent.groupId()?.let { chatRoomId ->
                 MarmotGroupEvent(
                    id = groupEvent.id,
                    encryptedContent = groupEvent.encryptedContent(),
                    nostrEventId = groupEvent.id,
                    createdAt = Instant.fromEpochSeconds(groupEvent.createdAt),
                    chatRoomId = chatRoomId,
                    userPublicKey = userPublicKey,
                    publicKey = groupEvent.pubKey,
                    expiresAt = groupEvent.expiration()?.let { Instant.fromEpochSeconds(it) },
                )
            }
        }
    }

    fun toMarmotInnerEvent(group: MlsGroup): MarmotInnerEvent? {
        return null;
    }

    /**
     * Decrypt the outer ChaCha20-Poly1305 layer, trying the current epoch's
     * exporter key first and falling back to retained epoch exporter keys.
     *
     * After a commit advances the epoch, late-arriving messages encrypted
     * with the previous epoch's exporter key would fail without this fallback.
     *
     * Returns null when neither the current epoch key nor any retained key
     * decrypts. This happens normally for commits/application messages from
     * epochs that predate our join (we never held those keys), so callers
     * should treat null as an expected "nothing to do here" outcome and log
     * at DEBUG, not as an error.
     */
    private fun tryDecryptOuterLayer(
        group: MlsGroup,
        encryptedContent: String,
    ): ByteArray? {
        // Try current epoch key first
        try {
            val exporterKey = group.exporterSecret()
            return GroupEventEncryption.decrypt(encryptedContent, exporterKey)
        } catch (e: Exception) {
            // Current epoch key failed — try retained epoch keys
            Log.e("MarmotGroupEvent", "Current Epoch Key Failed", e)
        }

        // Try retained epoch exporter keys (most recent first)
        throw MarmotMissingRetainedExporterSecretsException(
            "Torch currently doesn't support retainedExporterSecrets"
        )
//        val retainedKeys = groupManager.retainedExporterSecrets(groupId)
//        for (retainedKey in retainedKeys) {
//            try {
//                return GroupEventEncryption.decrypt(encryptedContent, retainedKey)
//            } catch (_: Exception) {
//                // This retained key didn't work — try the next one
//            }
//        }
//
//        return null
    }
}