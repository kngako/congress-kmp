package press.mantra.compose.nostr.frost

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Kind
import com.vitorpamplona.quartz.nip01Core.tags.dTag.DTag
import press.mantra.compose.managers.SharedKeyDerivation
import press.mantra.compose.nostr.frost.tags.FrostDerivationPathTag
import press.mantra.compose.nostr.frost.tags.FrostKeyTag

/**
 * What key a Marmot room signs with, announced into the room itself.
 *
 * The coordinator posts one as the room's first message, right after creating
 * it. Content is the group's ChillDKG threshold public key; the tags name the
 * ceremony that produced it and the path the room's id was derived at.
 *
 * ```
 *  coordinator --[ 30326 group key state ]-> everyone   "this room signs with K, at m/9420/0/0"
 * ```
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
 * The coordinator posts it, and the coordinator is untrusted by construction.
 * A receiver therefore verifies rather than believes: the room's id *is* the
 * threshold key derived at the path, so
 * `SharedKeyDerivation.marmotGroupId(content, path) == chatRoomId` has to hold
 * or the state is dropped. That is the same check
 * `FrostSigningManager.completedKey` made by rederiving, kept rather than
 * replaced -- this event makes the association explicit and cheap to look up,
 * not easier to forge.
 *
 * ### Replaceable, by this app rather than by a relay
 *
 * Like every kind in [FrostSigningEvents] this is a rumor inside a Marmot group
 * event, so no relay ever sees it and the addressable semantics of the 3xxxx
 * range never fire. [DTag] is the room id and the newest state per room wins,
 * which the local store enforces on its own. Being able to say it twice is what
 * matters in practice: a redelivered announcement, or a second member saying the
 * same true thing, folds away instead of accumulating.
 *
 * One room only ever names one key today. A group that re-runs its ceremony
 * derives a *different* room from the new key, so rotation in place does not
 * arise -- and if it ever does, the verification above is what has to change
 * first, because a rotated key no longer derives the room it is announced in.
 */
object GroupKeyStateEvent {
    /**
     * Sits with the signing family in the Marmot inner-event space. 30320-30325
     * are a signing session; this is the standing fact a session is opened
     * against, so it is adjacent rather than inside.
     */
    val KIND: Kind = 30326

    fun isGroupKeyStateKind(kind: Kind): Boolean = kind == KIND

    /**
     * The tags for a state naming [dkgSessionId], for the room derived at [path].
     *
     * The room id goes on as the `d` tag so the event is self-addressing: a
     * reader can tell which room a state belongs to without the envelope it
     * arrived in, which is what makes dropping a state announced into the wrong
     * room a check rather than an assumption.
     */
    fun assembleTags(
        chatRoomId: String,
        dkgSessionId: String,
        path: List<Long> = SharedKeyDerivation.MARMOT_ADMIN_GROUP_PATH
    ): Array<Array<String>> = arrayOf(
        DTag.assemble(chatRoomId),
        FrostKeyTag.assemble(dkgSessionId),
        FrostDerivationPathTag.assemble(path)
    )

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
}
