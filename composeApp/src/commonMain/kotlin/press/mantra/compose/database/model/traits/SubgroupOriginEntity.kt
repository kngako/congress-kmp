package press.mantra.compose.database.model.traits

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Kind

/**
 * A thing a group can decide deserves a room of its own.
 *
 * Four rows in four tables -- a dialect, an artifact, a translation, a project --
 * already carry the same three fields, and a subgroup made for any of them needs
 * exactly those three: the room it lives in becomes the parent, its name becomes
 * the proposed name, and its id and kind become what the parent's quorum
 * certifies the new room is *for*.
 *
 * It is an interface rather than four overloads of `SubgroupOrigin.of` so that
 * "which things can a subgroup be made for" is a question the compiler answers.
 * A fifth entity gains the capability by implementing this, and the picker, the
 * certificate and both directions of the UI pick it up without being told.
 *
 * ### Why `name` is here and is not an identity
 *
 * [name] is for naming a room and nothing else. It is deliberately not treated
 * as identifying the entity anywhere: a dialect's identity is its
 * `(language, country)` pair, since nothing in the schema makes a dialect unique
 * -- the id is a content hash including `createdAt`, there is no index, and the
 * rows are quorum-signed so they cannot be rewritten. Two rooms that typed
 * "Southern Sotho" and "Sesotho" hold the same dialect under two names. So the
 * link a subgroup carries is [id] and [subgroupOriginKind], and the name is only
 * ever what the room is called.
 *
 * ### Why the kind is on the row
 *
 * A reader holding an id alone would have to try four tables to find out what it
 * is looking at, and could not tell "a project this device does not hold" from
 * "not a project at all". Every one of these rows already knows its own event
 * kind -- most of them build one in `toXEvent()` -- so stating it costs a line
 * and saves the lookup. See `SubgroupOrigin`.
 */
interface SubgroupOriginEntity {
    /** The entity's own id: a nostr event id, content-hashed. */
    val id: HexKey

    /** What to call the room. Never read as an identity -- see the class comment. */
    val name: String

    /** The room this entity lives in, which is the room that would be the parent. */
    val chatRoomId: String

    /** The nip30303 kind of the event this row is, for a reader holding only the id. */
    val subgroupOriginKind: Kind
}
