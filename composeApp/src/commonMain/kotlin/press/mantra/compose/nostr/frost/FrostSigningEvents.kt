package press.mantra.compose.nostr.frost

import com.vitorpamplona.quartz.nip01Core.core.Kind
import press.mantra.compose.nostr.frost.tags.FrostKeyTag
import press.mantra.compose.nostr.frost.tags.FrostSessionIdTag
import press.mantra.compose.nostr.frost.tags.FrostSignerIdsTag

/**
 * The nostr kinds a FROST signing session is carried on.
 *
 * Like the ChillDKG kinds these are **rumor** kinds: they only ever exist
 * inside a NIP-17 gift wrap addressed to the group, so no relay sees them
 * unencrypted and the replaceable semantics normally implied by the 3xxxx range
 * never apply.
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
 * ### Why 3032x and not 3031x
 *
 * The DKG kinds run 30310-30316 and the nip30303 document kinds run 30300
 * upwards; those two have already met at 30310 and 30311, and
 * [press.mantra.compose.nostr.nip30303.SubmissionEvent] sits on 30312, which is
 * also the DKG's round-1 kind. They are kept apart today only by travelling on
 * different transports -- documents inside Marmot group events, rituals inside
 * NIP-17 wraps -- which is luck rather than design.
 *
 * Signing runs on the same transport as the DKG and in the same rooms, so it
 * starts at 30320 with a deliberate gap. Anything added to either family has
 * room to grow without a second accident.
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
