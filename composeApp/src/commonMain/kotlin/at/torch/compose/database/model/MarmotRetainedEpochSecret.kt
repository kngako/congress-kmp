package at.torch.compose.database.model

import androidx.room3.Entity
import androidx.room3.ForeignKey

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = ChatRoom::class,
            parentColumns = ["id"],
            childColumns = ["chatRoomId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    primaryKeys = ["chatRoomId", "epoch"]
)
data class MarmotRetainedEpochSecret(
    val chatRoomId: String,
    val epoch: Long,
    val senderDataSecret: ByteArray, // TODO: Encrypt this...
    val encryptionSecret: ByteArray, // TODO: Encrypt this...
    val leafCount: Int,
    val exporterSecret: ByteArray = ByteArray(0), // TODO: Encrypt this...
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as MarmotRetainedEpochSecret

        if (epoch != other.epoch) return false
        if (leafCount != other.leafCount) return false
        if (chatRoomId != other.chatRoomId) return false
        if (!senderDataSecret.contentEquals(other.senderDataSecret)) return false
        if (!encryptionSecret.contentEquals(other.encryptionSecret)) return false
        if (!exporterSecret.contentEquals(other.exporterSecret)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = epoch.hashCode()
        result = 31 * result + leafCount
        result = 31 * result + chatRoomId.hashCode()
        result = 31 * result + senderDataSecret.contentHashCode()
        result = 31 * result + encryptionSecret.contentHashCode()
        result = 31 * result + exporterSecret.contentHashCode()
        return result
    }

}