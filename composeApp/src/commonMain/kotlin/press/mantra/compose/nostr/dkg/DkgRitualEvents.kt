package press.mantra.compose.nostr.dkg

import press.mantra.compose.nostr.dkg.tags.DkgSessionIdTag
import press.mantra.compose.nostr.dkg.tags.DkgThresholdTag
import com.vitorpamplona.quartz.nip01Core.core.Kind

/**
 * The nostr kinds a ChillDKG ritual is carried on.
 *
 * These are **rumor** kinds: they only ever exist inside a NIP-17 gift wrap
 * addressed to the group, so no relay sees them unencrypted and the replaceable
 * semantics normally implied by the 3xxxx range never apply. They sit next to
 * the app's other private kinds (30300-30309) deliberately — the transport,
 * sealing and broadcast pipeline is the same one chat messages ride.
 *
 * Who talks to whom, in order:
 *
 * ```
 *  coordinator --[ 30310 proposal      ]-> everyone   "let's make a t-of-n key"
 *  participant --[ 30311 host key      ]-> everyone   host pubkey for SessionParams
 *  participant --[ 30312 round 1       ]-> everyone   ChillDkg.ParticipantMsg1
 *  coordinator --[ 30313 coord round 1 ]-> everyone   ChillDkg.CoordinatorMsg1
 *  participant --[ 30314 round 2       ]-> everyone   ChillDkg.ParticipantMsg2
 *  coordinator --[ 30315 certificate   ]-> everyone   ChillDkg.CoordinatorMsg2
 *  anyone      --[ 30316 failure       ]-> everyone   abort + blame
 * ```
 *
 * Everything is addressed to the whole group rather than point-to-point even
 * where the protocol only needs the coordinator to read it. NIP-17 wraps per
 * recipient anyway, the coordinator is untrusted by construction, and letting
 * every member watch the ritual is what makes the progress UI possible without
 * a second channel.
 */
object DkgRitualEvents {
    /** Coordinator opens a ritual. Threshold in [DkgThresholdTag]; membership is the wrap's p-tags. */
    val PROPOSAL: Kind = 30310

    /** A participant's long-term ChillDKG host public key (33-byte compressed, hex). */
    val HOST_KEY: Kind = 30311

    /** `ChillDkg.ParticipantMsg1` bytes, hex encoded. */
    val ROUND_1: Kind = 30312

    /** `ChillDkg.CoordinatorMsg1` bytes, hex encoded. */
    val COORDINATOR_ROUND_1: Kind = 30313

    /** `ChillDkg.ParticipantMsg2` bytes (a 64-byte signature), hex encoded. */
    val ROUND_2: Kind = 30314

    /** `ChillDkg.CoordinatorMsg2` bytes (the success certificate), hex encoded. */
    val CERTIFICATE: Kind = 30315

    /** Ritual abandoned. Content is the reason, for showing to the group. */
    val FAILURE: Kind = 30316

    /** Every kind above, for filtering inbound payloads in one check. */
    val ALL: Set<Kind> = setOf(
        PROPOSAL,
        HOST_KEY,
        ROUND_1,
        COORDINATOR_ROUND_1,
        ROUND_2,
        CERTIFICATE,
        FAILURE
    )

    fun isDkgRitualKind(kind: Kind): Boolean = kind in ALL

    /**
     * Tags for a ritual message. The session id is on every kind so a late
     * message from an abandoned attempt can be dropped rather than mixed in.
     */
    fun assembleTags(
        sessionId: String,
        threshold: Int? = null
    ): Array<Array<String>> = buildList {
        add(DkgSessionIdTag.assemble(sessionId))
        threshold?.let { add(DkgThresholdTag.assemble(it)) }
    }.toTypedArray()

    fun parseSessionId(tags: Array<Array<String>>): String? =
        tags.firstNotNullOfOrNull(DkgSessionIdTag::parse)?.sessionId

    fun parseThreshold(tags: Array<Array<String>>): Int? =
        tags.firstNotNullOfOrNull(DkgThresholdTag::parse)?.threshold
}
