package press.mantra.compose.nostr.frost

import com.vitorpamplona.quartz.nip01Core.core.Kind
import press.mantra.compose.nostr.frost.tags.FrostKeyTag
import press.mantra.compose.nostr.frost.tags.FrostSessionIdTag
import press.mantra.compose.nostr.frost.tags.FrostSignerIdsTag

/**
 * The nostr kinds a FROST signing session is carried on.
 *
 * These are **rumor** kinds: they only ever exist inside a Marmot group event,
 * MLS-encrypted to the group and then wrapped again under the group's exporter
 * secret, so no relay sees them and the replaceable semantics normally implied
 * by the 3xxxx range never apply.
 *
 * Who talks to whom, in order:
 *
 * ```
 *  proposer --[ 30320 proposal   ]-> everyone   the unsigned event to sign
 *  signer   --[ 30321 nonce      ]-> everyone   this device's public nonce
 *  proposer --[ 30322 signer set ]-> everyone   who is signing, and their aggregated nonce
 *  signer   --[ 30323 partial    ]-> everyone   this device's partial signature
 *  proposer --[ 30324 signature  ]-> everyone   the finished 64-byte signature
 *  anyone   --[ 30325 failure    ]-> everyone   abandon + blame
 * ```
 *
 * ### Why 3032x
 *
 * These share the inner-event space with the nip30303 document kinds, which run
 * 30300 up to [press.mantra.compose.nostr.nip30303.SubmissionEvent] at 30312 --
 * the same space, because both are Marmot inner events and both are dispatched
 * on kind by the same inbound path. Starting at 30320 leaves that family room to
 * grow into.
 *
 * The DKG's 30310-30316 look like a clash and are not: those exist only inside
 * NIP-17 gift wraps, and nothing reads a kind across both transports. It is
 * worth knowing that the numbers already overlap there -- the DKG's proposal and
 * host-key kinds sit on 30310 and 30311 alongside two nip30303 kinds, and its
 * round-1 kind is 30312, alongside SubmissionEvent -- because that separation is
 * an accident of routing rather than a decision, and the next family added
 * should not rely on it.
 */
object FrostSigningEvents {
    /**
     * Opens a session. Content is the unsigned nostr event, as JSON; the key to
     * sign with is named in [FrostKeyTag].
     */
    val PROPOSAL: Kind = 30320

    /** A signer's `IndividualNonce`, hex encoded. */
    val NONCE: Kind = 30321

    /**
     * The coordinator's chosen signers, in [FrostSignerIdsTag], with their
     * `AggregatedNonce` as the content, hex encoded.
     */
    val SIGNER_SET: Kind = 30322

    /** A signer's 32-byte partial signature, hex encoded. */
    val PARTIAL_SIGNATURE: Kind = 30323

    /** The finished 64-byte BIP-340 signature over the event id, hex encoded. */
    val SIGNATURE: Kind = 30324

    /** Session abandoned. Content is the reason, for showing to the group. */
    val FAILURE: Kind = 30325

    /** Every kind above, for filtering inbound payloads in one check. */
    val ALL: Set<Kind> = setOf(
        PROPOSAL,
        NONCE,
        SIGNER_SET,
        PARTIAL_SIGNATURE,
        SIGNATURE,
        FAILURE
    )

    fun isFrostSigningKind(kind: Kind): Boolean = kind in ALL

    /**
     * Tags for a signing message. The session id is on every kind so a message
     * from an abandoned attempt can be dropped rather than mixed in.
     */
    fun assembleTags(
        sessionId: String,
        dkgSessionId: String? = null,
        signerIds: List<Int>? = null
    ): Array<Array<String>> = buildList {
        add(FrostSessionIdTag.assemble(sessionId))
        dkgSessionId?.let { add(FrostKeyTag.assemble(it)) }
        signerIds?.let { add(FrostSignerIdsTag.assemble(it)) }
    }.toTypedArray()

    fun parseSessionId(tags: Array<Array<String>>): String? =
        tags.firstNotNullOfOrNull(FrostSessionIdTag::parse)?.sessionId

    fun parseKey(tags: Array<Array<String>>): String? =
        tags.firstNotNullOfOrNull(FrostKeyTag::parse)?.dkgSessionId

    fun parseSignerIds(tags: Array<Array<String>>): List<Int>? =
        tags.firstNotNullOfOrNull(FrostSignerIdsTag::parse)?.signerIds
}
