package press.mantra.compose.database.model

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import press.mantra.compose.database.model.traits.LocalStoreEntity
import press.mantra.compose.database.model.traits.TimestampedEntity
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

    override val createdAt: Instant = Clock.System.now(),
    override val updatedAt: Instant = createdAt,
    override val savedAt: Instant = createdAt,
): TimestampedEntity, LocalStoreEntity {
    fun isCoordinator(): Boolean = coordinatorPublicKey == userPublicKey
}
