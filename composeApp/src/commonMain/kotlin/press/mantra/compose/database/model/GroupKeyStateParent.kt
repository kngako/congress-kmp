package press.mantra.compose.database.model

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * One group that has certified a room as its child, and the signature saying so.
 *
 * A row per parent, because a room can have more than one: it is founded with
 * one and may be adopted by another later, and an adoption is the parent
 * certifying plus the child re-announcing its key state with the pair added. See
 * `docs/subgroups-multiple-parents.md`.
 *
 * These rows were two nullable columns on [GroupKeyState] while a lineage could
 * only ever be one link. Nothing about their meaning changed in the move.
 *
 * ### The primary key is the rule
 *
 * `(chatRoomId, parentChatRoomId)` -- one certificate per child per parent. A
 * second entry for a parent already in the set is one parent asked twice and is
 * refused by `SubgroupLineage.of` before it gets here; the key is what makes
 * that a property of the store rather than of a reader remembering.
 *
 * ### Verified before it is written, every time
 *
 * A row exists only where `SubgroupBirthCertificateEvent.certifies` has said
 * that this parent's quorum really signed for this room -- see
 * `GroupKeyStateManager.stateFrom`, which drops the **whole** state when any one
 * parent's certificate fails. So the presence of a row is itself the claim that
 * the lineage was checked, and there is no state in which some parents are
 * believed and others are not.
 *
 * [birthCertificateJson] is the certificate whole rather than its signature,
 * so a reader can check it again from the row alone. A signature plus a rule for
 * rebuilding the event it covers is a rule that breaks silently the first time
 * the certificate's shape changes: a rebuild differing by one byte hashes to an
 * id whose signature fails and is indistinguishable from a forgery.
 *
 * ### The cascade points at the state, never at the parent's room
 *
 * `ON DELETE CASCADE` from [GroupKeyState] is what keeps a lineage from
 * outliving the state that claimed it -- and it is what lets a re-announcement
 * that drops a parent actually drop it, since `GroupKeyStateDao.replace`
 * rewrites these rows inside the same transaction.
 *
 * [parentChatRoomId] is deliberately **not** a foreign key onto `ChatRoom`. A
 * self-referential cascade would mean deleting a parent room deletes its
 * children's key states, and then by their own cascades those rooms' messages,
 * participants, signing sessions and signed events -- a user tidying up a group
 * they left would destroy a group they are still in. `RESTRICT` would be no
 * better: it would make a parent undeletable while a child row exists, which is
 * a foreign key deciding a product question. And this routinely names a room the
 * device does not have at all, since a member of a subgroup need never have been
 * in its parent, so a dangling value is the normal, expected state and readers
 * resolve it with a lookup allowed to return null.
 */
@Entity(
    primaryKeys = ["chatRoomId", "parentChatRoomId"],
    foreignKeys = [
        ForeignKey(
            entity = GroupKeyState::class,
            parentColumns = ["chatRoomId"],
            childColumns = ["chatRoomId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        // The composite primary key already indexes chatRoomId as its leftmost
        // column, so only the other direction needs one: "which rooms has this
        // group certified", which is how a parent finds the children whose state
        // this device holds.
        Index("parentChatRoomId"),
    ],
)
data class GroupKeyStateParent(
    /** The child: the room whose key state carries this parentage. */
    val chatRoomId: String,

    /** The parent's room id, which is also the key its certificate verifies against. */
    val parentChatRoomId: HexKey,

    /** The parent's signed certificate, whole, as JSON. */
    val birthCertificateJson: String,
)
