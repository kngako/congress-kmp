package press.mantra.compose.database.model

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Kind
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * One ritual message from one member, as one profile received it — keyed so a
 * duplicate delivery overwrites rather than accumulates, because relays redeliver
 * and feeding the same round-1 message in twice would make the coordinator's
 * participant list the wrong length.
 *
 * Covers the host key, round 1 and round 2; the coordinator's own broadcasts
 * live on [DkgSession] because there is only ever one of each.
 *
 * ### Why the receiving profile is part of the key
 *
 * The payload is a public protocol value and identical for everyone who sees it,
 * so a row looks at first like the ceremony's rather than a profile's. What is
 * per-profile is not the bytes but the *having received them*: `DkgSession`
 * deliberately stores inputs instead of protocol state, and the messages are
 * those inputs, recomputed from alongside that profile's own randomness. A
 * profile's view of the ritual is how far it can recompute, and that is what this
 * table holds.
 *
 * It also keeps the cascade. [DkgSession]'s key is (`id`, `userPublicKey`), and a
 * foreign key must name a unique parent, so a child keyed by the ceremony alone
 * could not reference it at all -- and deleting a room would leave its rituals'
 * messages behind.
 */
@Entity(
    primaryKeys = ["sessionId", "userPublicKey", "participantPublicKey", "kind"],
    foreignKeys = [
        ForeignKey(
            entity = DkgSession::class,
            parentColumns = ["id", "userPublicKey"],
            childColumns = ["sessionId", "userPublicKey"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index("sessionId", "userPublicKey"),
    ],
)
data class DkgParticipantMessage(
    val sessionId: String,

    /** Whose profile received it — the [DkgSession] row this belongs to. */
    val userPublicKey: HexKey,

    /** The member's nostr public key — who sent it. */
    val participantPublicKey: HexKey,

    /** One of the `DkgRitualEvents` kinds. */
    val kind: Kind,

    /** Hex of the protocol bytes, or of the host public key for a host-key message. */
    val payload: HexKey,

    val createdAt: Instant = Clock.System.now(),
)
