package press.mantra.compose.database.model

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import press.mantra.compose.database.model.traits.LocalStoreEntity
import press.mantra.compose.database.model.traits.TimestampedEntity
import press.mantra.compose.database.model.types.DkgApprovalStep
import press.mantra.compose.database.model.types.DkgRitualStage
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * One ChillDKG ritual, as this device sees it.
 *
 * Deliberately stores inputs rather than protocol state. `ChillDKG`'s
 * participant and coordinator steps are pure functions of their inputs — the
 * only randomness enters through the `random`/`auxRand` arguments — so keeping
 * [round1Random], [round2AuxRandom] and the received messages is enough to
 * recompute every intermediate state on demand. That is what lets a ritual
 * survive the app being killed halfway through, which matters because a DKG
 * needs every member to finish and members close apps.
 *
 * [round1Random] and [round2AuxRandom] are secret: they reconstruct this
 * device's share. They live here under the same "encrypted local storage"
 * assumption `MlsGroupState` already relies on.
 */
@Entity(
    foreignKeys = [
        ForeignKey(
            entity = ChatRoom::class,
            parentColumns = ["id"],
            childColumns = ["chatRoomId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index("chatRoomId"),
    ],
)
data class DkgSession(
    /** Minted by the coordinator, carried on every message as `dkg_session`. */
    @PrimaryKey
    val id: String,

    val chatRoomId: String,

    /** The member running the ritual — by construction, the room's creator. */
    val coordinatorPublicKey: HexKey,

    /** Whose device this row belongs to, for multi-account support. */
    val userPublicKey: HexKey,

    /** The `t` of t-of-n, taken from the quorum chosen when the group was made. */
    val threshold: Int,

    /** The `n`: how many members have to show up before params can be built. */
    val participantCount: Int,

    val stage: DkgRitualStage = DkgRitualStage.COLLECTING_HOST_KEYS,

    /** This device's ChillDKG host public key (33-byte compressed, hex). */
    val hostPublicKey: HexKey,

    /** Secret. 32 bytes of fresh randomness for `participantStep1`. */
    val round1Random: HexKey,

    /** Secret. 32 bytes of fresh randomness for `participantStep2`. */
    val round2AuxRandom: HexKey,

    /** `CoordinatorMsg1` once it arrives, hex. */
    val coordinatorRound1: HexKey? = null,

    /** `CoordinatorMsg2` — the success certificate — once it arrives, hex. */
    val certificate: HexKey? = null,

    /** Result: the group's FROST threshold public key, hex. */
    val thresholdPublicKey: HexKey? = null,

    /** Result, secret: this device's FROST secret share, hex. */
    val secretShare: HexKey? = null,

    /** Result: recovery data, to be backed up alongside the host key. */
    val recoveryData: HexKey? = null,

    val failureReason: String? = null,

    /**
     * When this device's owner approved publishing their host key, and with it
     * joining the ceremony at all. Null until they do, and nothing of theirs goes
     * out before it is set.
     *
     * The ritual is otherwise driven entirely by arriving messages, which would
     * mean a member is enrolled in the group's permanent signing quorum by a relay
     * delivering an event to a phone in their pocket. Each of these three is a
     * separate decision because each publishes something different and commits the
     * member to something different -- see `ChillDkgRitualManager.pendingApproval`.
     *
     * Set at creation for the member who opened the ceremony: starting one is
     * already the act of agreeing to be in it.
     */
    val hostKeyApprovedAt: Instant? = null,

    /** When they approved publishing their contribution to the key. */
    val round1ApprovedAt: Instant? = null,

    /** When they approved signing to confirm the combined result. */
    val round2ApprovedAt: Instant? = null,

    /**
     * The furthest step this device has already asked its owner about, so the chat
     * line asking for it is written once.
     *
     * One column rather than a flag per step because the steps are strictly
     * ordered: reaching [DkgApprovalStep.ROUND_1] means the host key was asked for
     * and granted. ChatMessage has no session to key a row against and no unique
     * constraint to lean on, so the guard has to live here.
     */
    val approvalRequestedThrough: DkgApprovalStep? = null,

    override val createdAt: Instant = Clock.System.now(),
    override val updatedAt: Instant = createdAt,
    override val savedAt: Instant = createdAt,
): TimestampedEntity, LocalStoreEntity {
    fun isCoordinator(): Boolean = coordinatorPublicKey == userPublicKey
}
