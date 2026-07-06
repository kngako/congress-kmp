package at.torch.compose.database.model

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Ignore
import androidx.room3.PrimaryKey
import at.torch.compose.database.model.traits.LocalStoreEntity
import at.torch.compose.database.model.traits.SoftDeletableEntity
import at.torch.compose.database.model.traits.TimestampedEntity
import at.torch.compose.database.model.traits.UserViewableEntity
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupData
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEvent
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEventEncryption
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroup
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Kind
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.people.taggedUsers
import com.vitorpamplona.quartz.nip14Subject.subject
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.time.Clock
import kotlin.time.Instant

@Entity(
    foreignKeys = [

    ],
)
data class MarmotCommitResult( // TODO: Rename this to GiftWrapPayload...
    @PrimaryKey
    val id: HexKey,

    val userPublicKey: HexKey,

    val peerKeyPackageEventId: HexKey,

    val chatRoomId: HexKey,

    val isOneMemberInitialGroupCreation: Boolean,

    /**
     * commitBytes is the raw TLS-encoded Commit struct (RFC 9420 §12.4)
     */
    val commitBytes: ByteArray,
    val welcomeBytes: ByteArray?,
    val groupInfoBytes: ByteArray?,
    /**
     * Fully-framed commit ready for the MIP-03 outer ChaCha20 encryption.
     * Wire format: MlsMessage(version=mls10, wireFormat=mls_public_message, payload=PublicMessage(...)).
     */
    val framedCommitBytes: ByteArray = commitBytes,
    /**
     * `MLS-Exporter("marmot", "group-event", 32)` evaluated at the **pre-commit**
     * epoch (N) — the key the group had when this commit was computed, before
     * local state advanced to N+1. Publishers of the kind:445 MUST ChaCha20-wrap
     * the commit with this key (RFC 9420 §12.4 and MDK parity). Using the
     * post-commit (N+1) key makes the commit unreadable to existing members
     * still at epoch N — the exact scenario that caused Eden to stall at
     * epoch 1 and never decrypt any of David's subsequent messages.
     *
     * Empty by default for the test-only entry points that don't need it.
     */
    val preCommitExporterSecret: ByteArray = ByteArray(0),

    /**
     * Current time
     */
    override val createdAt: Instant = Clock.System.now(),
    override val updatedAt: Instant = createdAt,
    override val savedAt: Instant = Clock.System.now(),
    override val viewedAt: Instant? = null,
    override val deletedAt: Instant? = null, // Who this message should be seen by (used to derive conversationId)
): TimestampedEntity,
    LocalStoreEntity,
    UserViewableEntity,
    SoftDeletableEntity {
    @Ignore
    private val logger = Logger.withTag(TAG)


    companion object {
        const val TAG = "MarmotCommitResult"

    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as MarmotCommitResult

        if (isOneMemberInitialGroupCreation != other.isOneMemberInitialGroupCreation) return false
        if (id != other.id) return false
        if (userPublicKey != other.userPublicKey) return false
        if (chatRoomId != other.chatRoomId) return false
        if (!commitBytes.contentEquals(other.commitBytes)) return false
        if (!welcomeBytes.contentEquals(other.welcomeBytes)) return false
        if (!groupInfoBytes.contentEquals(other.groupInfoBytes)) return false
        if (!framedCommitBytes.contentEquals(other.framedCommitBytes)) return false
        if (!preCommitExporterSecret.contentEquals(other.preCommitExporterSecret)) return false
        if (createdAt != other.createdAt) return false
        if (updatedAt != other.updatedAt) return false
        if (savedAt != other.savedAt) return false
        if (viewedAt != other.viewedAt) return false
        if (deletedAt != other.deletedAt) return false
        if (logger != other.logger) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isOneMemberInitialGroupCreation.hashCode()
        result = 31 * result + id.hashCode()
        result = 31 * result + userPublicKey.hashCode()
        result = 31 * result + chatRoomId.hashCode()
        result = 31 * result + commitBytes.contentHashCode()
        result = 31 * result + (welcomeBytes?.contentHashCode() ?: 0)
        result = 31 * result + (groupInfoBytes?.contentHashCode() ?: 0)
        result = 31 * result + framedCommitBytes.contentHashCode()
        result = 31 * result + preCommitExporterSecret.contentHashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + updatedAt.hashCode()
        result = 31 * result + savedAt.hashCode()
        result = 31 * result + (viewedAt?.hashCode() ?: 0)
        result = 31 * result + (deletedAt?.hashCode() ?: 0)
        result = 31 * result + logger.hashCode()
        return result
    }
}