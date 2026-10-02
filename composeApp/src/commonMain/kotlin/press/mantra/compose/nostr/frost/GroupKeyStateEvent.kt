package press.mantra.compose.nostr.frost

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Kind
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import com.vitorpamplona.quartz.nip01Core.crypto.Nip01Crypto
import com.vitorpamplona.quartz.nip01Core.tags.dTag.DTag
import press.mantra.compose.managers.SharedKeyDerivation
import press.mantra.compose.nostr.frost.tags.FrostDerivationPathTag
import press.mantra.compose.nostr.frost.tags.FrostKeyTag
import press.mantra.compose.nostr.subgroup.SubgroupBirthCertificateEvent
import press.mantra.compose.nostr.subgroup.SubgroupLineage
import press.mantra.compose.nostr.subgroup.SubgroupParentage
import press.mantra.compose.nostr.subgroup.tags.SubgroupBirthCertificateTag
import press.mantra.compose.nostr.subgroup.tags.SubgroupParentTag

/**
 * What key a Marmot room signs with, signed by the group whose key it is.
 *
 * Nobody announces this; the group *agrees* it, and does so before the room it
 * is about exists. What the ceremony's coordinator puts into the NIP-17 room the
 * ceremony ran in is a [FrostSigningEvents.PROPOSAL] carrying this event
 * unsigned, and the state comes into existence when a quorum has signed it --
 * the same shape a dialect or an artifact is created in.
 *
 * ```
 *  coordinator --[ 30320 proposal over a 30326 ]-> the ceremony's room
 *                                                  "shall we say our room will sign with K?"
 *  ...the session runs, on gift wraps...
 *  everyone holds the signed 30326                 "the group says that room signs with K"
 *  ...somebody creates the room...
 *  everyone files it as the room appears           GroupKeyStateManager.adopt
 * ```
 *
 * A room *is* its key, so this can be signed before the room exists and checked
 * without it: everything a reader needs is the event. See
 * `GroupKeyStateManager` for why the order was turned around.
 *
 * Content is the group's ChillDKG threshold public key; the tags name the
 * ceremony that produced it and the path the room's id was derived at.
 *
 * ### What it is for
 *
 * A signer holds a different secret share under every ceremony it took part in,
 * and signing with the wrong one produces a partial signature that cannot
 * aggregate. This is the record that says which. A member reads the state,
 * follows [FrostKeyTag] to the `DkgSession` row their own device already holds,
 * and takes the share from there -- the association travels, the share does not.
 *
 * Nothing secret is in here, and that is not an accident. Every member of the
 * room can read it, so a share put on this event would be every member holding
 * every other member's share, which is a 1-of-n key wearing a t-of-n's clothes.
 *
 * ### Trusted no further than it can be checked
 *
 * Two independent checks, and a state has to pass both -- see [isSignedByGroup]
 * and `GroupKeyState.verifies`.
 *
 * The derivation is the older one and still the load-bearing one: the room's id
 * *is* the threshold key derived at the path, so
 * `SharedKeyDerivation.marmotGroupId(content, path) == chatRoomId` has to hold.
 * A state that fails it names a key the room was not made from, whoever signed
 * it.
 *
 * The signature is what the proposal flow adds. A state now carries a BIP-340
 * signature by the key that same derivation reaches -- which, since a room's id
 * *is* that key, means a state is signed by the room it is about, and no single
 * member can produce it. So a member cannot decide on their own what the room
 * signs with, even truthfully. The two checks answer different questions and
 * neither replaces the other: the derivation says the state is true, the
 * signature says the group said it.
 *
 * ### Replaceable, by this app rather than by a relay
 *
 * Like every kind in [FrostSigningEvents] this only ever lives inside an
 * encryption a relay cannot open -- a Marmot group event, or the gift wrap the
 * proposal for it travels in -- so no relay sees it and the addressable
 * semantics of the 3xxxx range never fire. [DTag] is the room id and the newest
 * state per room wins, which the local store enforces on its own. Being able to
 * say it twice is what matters in practice: a session replayed after a crash, or
 * a second proposal for the same true state, folds away instead of accumulating.
 *
 * One room only ever names one key today. A group that re-runs its ceremony
 * derives a *different* room from the new key, so rotation in place does not
 * arise -- and if it ever does, the verification above is what has to change
 * first, because a rotated key no longer derives the room it names.
 */
object GroupKeyStateEvent {
    /**
     * Sits with the signing family in the inner-event space. 30320-30325 are a
     * signing session; this is the standing fact a session is opened against, so
     * it is adjacent rather than inside.
     */
    val KIND: Kind = 30326

    fun isGroupKeyStateKind(kind: Kind): Boolean = kind == KIND

    /**
     * The tags for a state naming [dkgSessionId], for the room derived at [path].
     *
     * The room id goes on as the `d` tag so the event is self-addressing, and
     * that is now load-bearing rather than convenient: a state is signed in a
     * room that is not the one it is about, so the `d` tag is the only thing
     * that says which room it belongs to. What stops it naming a room it has no
     * business naming is the derivation, not the envelope -- see
     * `GroupKeyStateManager.stateFrom`.
     */
    fun assembleTags(
        chatRoomId: String,
        dkgSessionId: String,
        path: List<Long> = SharedKeyDerivation.MARMOT_ADMIN_GROUP_PATH,
        lineage: SubgroupLineage? = null
    ): Array<Array<String>> = arrayOf(
        DTag.assemble(chatRoomId),
        FrostKeyTag.assemble(dkgSessionId),
        FrostDerivationPathTag.assemble(path)
    ) + (
        lineage?.parents?.flatMap {
            // Both or neither, and **adjacent**: one parent's id immediately
            // followed by that parent's certificate, in the canonical order
            // `SubgroupLineage` sorts them into. A state carrying one half
            // without the other is dropped on read, so emitting one is only a
            // way to make a state nobody will believe.
            //
            // The pairing is what a build predating several parents depends on.
            // Such a reader takes the first tag of each name and checks them
            // against each other: emitted in pairs it gets one real parent and
            // one real certificate for it, verifies them, and files a true but
            // partial lineage. Emitted as all the ids and then all the
            // certificates it would pair the first id with a certificate that
            // may belong to another parent, fail `certifies`, and drop the whole
            // state -- losing the row that says which ceremony's share signs for
            // this room, which is a worse outcome than not knowing about the
            // second parent. See `SubgroupLineage`.
            listOf(
                SubgroupParentTag.assemble(it.parentChatRoomId),
                SubgroupBirthCertificateTag.assemble(it.certificate)
            )
        }?.toTypedArray() ?: emptyArray()
    )

    /**
     * The lineage the tags claim, or null if they claim none or claim one that
     * cannot be read.
     *
     * The two outcomes share a null for the reason every parentage reader here
     * does: a tag that is present and unparseable looks exactly like an absent
     * one through a parser. [claimsParentage] is what tells them apart, and
     * `GroupKeyStateManager.stateFrom` asks it first -- a state claiming a parent
     * in a form nothing can check is refused rather than filed as top-level.
     *
     * Unreadable here means any of: an odd number of halves, a tag of either
     * name that does not parse, or the same parent named twice. Each is a lineage
     * this device cannot state back, and completing one from the other halves
     * would be this device deciding what the group meant.
     *
     * **Pairing is positional.** The *n*th `parent_group` goes with the *n*th
     * `birth_certificate`, which is how [assembleTags] writes them and how a
     * reader that only ever looks at the first of each reads them. Nothing is
     * matched up by looking inside the certificates: whether the pair agrees is
     * `SubgroupBirthCertificateEvent.certifies`' question, asked once per pair,
     * and a mismatched pair must fail it rather than be quietly re-paired into
     * one that passes.
     */
    fun parseLineage(tags: Array<Array<String>>): SubgroupLineage? {
        val parentTagCount = tags.count { it.isNotEmpty() && it[0] == SubgroupParentTag.TAG_NAME }
        val certificateTagCount =
            tags.count { it.isNotEmpty() && it[0] == SubgroupBirthCertificateTag.TAG_NAME }

        if (parentTagCount == 0 || parentTagCount != certificateTagCount) return null

        val parentChatRoomIds = tags.mapNotNull(SubgroupParentTag::parse)
            .map { it.parentChatRoomId }
        val certificates = tags.mapNotNull(SubgroupBirthCertificateTag::parse)
            .map { it.certificate }

        // Counted by tag name above and by successful parse here, so a half that
        // is present and broken is told from one that was never written. Only
        // the first would shift the pairing.
        if (parentChatRoomIds.size != parentTagCount) return null
        if (certificates.size != certificateTagCount) return null

        return SubgroupLineage.of(
            parentChatRoomIds.zip(certificates) { parentChatRoomId, certificate ->
                SubgroupParentage(
                    parentChatRoomId = parentChatRoomId,
                    certificate = certificate
                )
            }
        )
    }

    /**
     * Whether the tags carry any half of a parentage.
     *
     * The question [parseLineage] cannot answer, because a tag that is present
     * and unparseable reads as absent through it. A state claiming a parent in a
     * form nothing can check must be refused rather than quietly filed as an
     * ordinary top-level group, so this looks at the tag names alone.
     */
    fun claimsParentage(tags: Array<Array<String>>): Boolean =
        tags.any {
            it.isNotEmpty() &&
                (it[0] == SubgroupParentTag.TAG_NAME || it[0] == SubgroupBirthCertificateTag.TAG_NAME)
        }

    /** The room this state is about, or null if it names none. */
    fun parseChatRoomId(tags: Array<Array<String>>): String? =
        tags.firstOrNull { it.size > 1 && it[0] == DTag.TAG_NAME }?.get(1)?.ifBlank { null }

    /** The ceremony whose share signs for this room, or null if it names none. */
    fun parseDkgSessionId(tags: Array<Array<String>>): String? =
        tags.firstNotNullOfOrNull(FrostKeyTag::parse)?.dkgSessionId

    /** The derivation path, or null if it carries none or an unwalkable one. */
    fun parsePath(tags: Array<Array<String>>): List<Long>? =
        tags.firstNotNullOfOrNull(FrostDerivationPathTag::parse)?.path

    /**
     * The threshold public key a state announces, or null when the content is
     * not one.
     *
     * Shape only -- 33 compressed bytes of hex. Whether it is *the* key for the
     * room is settled by rederiving the room id from it, not by looking at it.
     */
    fun parseThresholdPublicKey(content: String): HexKey? =
        content.trim()
            .takeIf { it.length == 66 && it.all { char -> char.isDigit() || char in 'a'..'f' || char in 'A'..'F' } }

    /**
     * Whether the room with id [chatRoomId] signed [event].
     *
     * Three things, and all three are needed. The author has to be the room, or
     * a real signature by some other group -- or by the same group in another of
     * its rooms -- would pass. The id has to be the hash of the fields it is
     * sitting next to, or the signature covers a message that is not this event
     * and the fields could then say anything. And the signature has to verify.
     *
     * ### Why a room id is enough
     *
     * A room's id *is* the group's threshold key derived at the room's path --
     * see `shared-key-derivation.md`, and `GroupKeyState.verifies` and
     * `FrostSigningManager.signingPath`, both of which hold that invariant from
     * their own ends. So the key a signature has to verify against is not looked
     * up; it is the id of the room the event was found in.
     *
     * That is what makes a group's signature checkable by a member holding
     * nothing else. No `GroupKeyState` row, no threshold key, no derivation path,
     * no ceremony -- which is exactly the position a member added after the
     * ceremony is in, and the reason `docs/member-chronicle.md` can hand them
     * history without asking them to trust whoever sent it.
     *
     * Everything is caught, because every input is off the wire: a pubkey that
     * is not a point, a signature that is not 64 bytes, hex that is not hex.
     * All of them mean the same thing here, which is no.
     */
    fun isSignedByRoom(
        event: Event,
        chatRoomId: HexKey
    ): Boolean = runCatching {
        if (!event.pubKey.equals(chatRoomId, ignoreCase = true)) return false

        val hashes = EventHasher.hashIdCheck(
            id = event.id,
            pubKey = event.pubKey,
            createdAt = event.createdAt,
            kind = event.kind,
            tags = event.tags,
            content = event.content
        )
        if (!hashes) return false

        Nip01Crypto.verify(
            signature = event.sig.hexToByteArray(),
            hash = event.id.hexToByteArray(),
            pubKey = event.pubKey.hexToByteArray()
        )
    }.getOrDefault(false)

    /**
     * Whether the room derived from [thresholdPublicKey] at [path] actually
     * signed [event].
     *
     * Note what is being checked against what: the key in the *content*, walked
     * to the path in the *tags*, is what has to have signed. Since that walk is
     * also the room's id, the author of a state that passes is the room it
     * belongs to -- so a state signed by one group about another group's key
     * fails here, which is the point. Only a room may say what it signs with.
     *
     * The walk is the only thing this adds to [isSignedByRoom], and it is inside
     * the catch for the same reason everything else is: [thresholdPublicKey]
     * comes off the wire, and a key that is not a point is a no rather than a
     * throw.
     */
    fun isSignedByGroup(
        event: Event,
        thresholdPublicKey: HexKey,
        path: List<Long>
    ): Boolean = runCatching {
        isSignedByRoom(event, SharedKeyDerivation.marmotGroupId(thresholdPublicKey, path))
    }.getOrDefault(false)
}
