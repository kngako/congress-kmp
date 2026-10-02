package press.mantra.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow
import press.mantra.compose.database.model.GroupKeyState
import press.mantra.compose.database.model.GroupKeyStateParent
import press.mantra.compose.database.model.intermdiate.LocalGroupKeyState

@Dao
abstract class GroupKeyStateDao {
    @Query("SELECT * FROM GroupKeyState WHERE chatRoomId = :chatRoomId")
    abstract suspend fun getByChatRoomId(chatRoomId: String): GroupKeyState?

    @Query("SELECT * FROM GroupKeyState WHERE chatRoomId = :chatRoomId")
    abstract fun observeByChatRoomId(chatRoomId: String): Flow<GroupKeyState?>

    /**
     * A state with the groups that have certified it, which is how a lineage
     * should always be read -- the two are one signed statement.
     */
    @Transaction
    @Query("SELECT * FROM GroupKeyState WHERE chatRoomId = :chatRoomId")
    abstract suspend fun getLocalByChatRoomId(chatRoomId: String): LocalGroupKeyState?

    @Transaction
    @Query("SELECT * FROM GroupKeyState WHERE chatRoomId = :chatRoomId")
    abstract fun observeLocalByChatRoomId(chatRoomId: String): Flow<LocalGroupKeyState?>

    /** Every room that signs with one ceremony's key. One, today. */
    @Query("SELECT * FROM GroupKeyState WHERE dkgSessionId = :dkgSessionId")
    abstract suspend fun getByDkgSessionId(dkgSessionId: String): List<GroupKeyState>

    /**
     * Every subgroup of [parentChatRoomId] this device holds a verified state
     * for.
     *
     * Verified is the whole of what these rows are: a parentage only reaches the
     * table once its birth certificate has been checked against that parent's
     * signature, so this is a list of children the parent really did certify
     * rather than a list of rooms claiming a parent.
     *
     * It is not the whole list. A subgroup whose room this device does not have
     * leaves no state here at all, and the certificates in `GroupSignedEvent` are
     * the fuller answer -- see `SubgroupManager.subgroupsOf`.
     *
     * A child with two parents appears in both parents' answers, which is the
     * point of the join: the row that makes it this parent's child says nothing
     * about who else certified it.
     */
    @Transaction
    @Query(
        "SELECT GroupKeyState.* FROM GroupKeyState " +
            "JOIN GroupKeyStateParent ON GroupKeyStateParent.chatRoomId = GroupKeyState.chatRoomId " +
            "WHERE GroupKeyStateParent.parentChatRoomId = :parentChatRoomId " +
            "ORDER BY GroupKeyState.announcedAt DESC"
    )
    abstract suspend fun getByParentChatRoomId(parentChatRoomId: String): List<LocalGroupKeyState>

    @Transaction
    @Query(
        "SELECT GroupKeyState.* FROM GroupKeyState " +
            "JOIN GroupKeyStateParent ON GroupKeyStateParent.chatRoomId = GroupKeyState.chatRoomId " +
            "WHERE GroupKeyStateParent.parentChatRoomId = :parentChatRoomId " +
            "ORDER BY GroupKeyState.announcedAt DESC"
    )
    abstract fun observeByParentChatRoomId(parentChatRoomId: String): Flow<List<LocalGroupKeyState>>

    /** The parents of one room, as rows. Prefer [getLocalByChatRoomId]. */
    @Query("SELECT * FROM GroupKeyStateParent WHERE chatRoomId = :chatRoomId")
    abstract suspend fun getParentsByChatRoomId(chatRoomId: String): List<GroupKeyStateParent>

    @Upsert
    abstract suspend fun upsert(groupKeyState: GroupKeyState)

    @Upsert
    abstract suspend fun upsertParents(parents: List<GroupKeyStateParent>)

    @Query("DELETE FROM GroupKeyStateParent WHERE chatRoomId = :chatRoomId")
    abstract suspend fun deleteParentsByChatRoomId(chatRoomId: String)

    /**
     * Files a state and its whole lineage, keeping the newest one per room.
     *
     * This is where the event's replaceable semantics actually happen. Relays
     * never see a `GroupKeyStateEvent` -- it lives inside a Marmot group and is
     * applied from a completed signing session -- so nothing upstream applies
     * the 3xxxx replacement rule, and a state that lands twice would otherwise
     * be two rows racing for one primary key.
     *
     * Older states are dropped rather than applied, so a session replayed after
     * a crash cannot walk the room back to a state it has already moved past. A
     * state at the same instant is kept as a no-op: two runs at the same true
     * state agree by construction, since both derived it from the room they are
     * standing in.
     *
     * ### The lineage is replaced, not merged, and that is the whole reason this
     * is a transaction
     *
     * A key state is replaceable and the newest wins **wholesale**, so the set of
     * parents it names is the set -- not an addition to whatever was there. That
     * is what makes a disavowal expressible at all: a room drops a parent by
     * re-announcing without it, and if these rows were merged rather than
     * rewritten the dropped parent would survive the statement that dropped it.
     *
     * Deleting before inserting inside one transaction is also what stops a
     * reader seeing a state beside somebody else's parents. Were the two writes
     * separate, a concurrent `getLocalByChatRoomId` could land between them and
     * read a state with no lineage, or with the old one -- both of which look
     * exactly like a true answer.
     *
     * Returns the state now on file, with the lineage now on file beside it.
     */
    @Transaction
    open suspend fun replace(
        groupKeyState: GroupKeyState,
        parents: List<GroupKeyStateParent> = emptyList(),
    ): LocalGroupKeyState {
        val known = getByChatRoomId(groupKeyState.chatRoomId)

        if (known != null && known.announcedAt >= groupKeyState.announcedAt) {
            return LocalGroupKeyState(
                keyState = known,
                parents = getParentsByChatRoomId(known.chatRoomId)
            )
        }

        val stamped = groupKeyState.copy(
            createdAt = known?.createdAt ?: groupKeyState.createdAt,
            updatedAt = groupKeyState.createdAt
        )
        upsert(stamped)

        // The state's own row has to exist before its parents', because they
        // reference it. Room turns foreign keys off around a migration but not
        // around a transaction, so the order here is load-bearing rather than
        // tidy.
        deleteParentsByChatRoomId(stamped.chatRoomId)
        if (parents.isNotEmpty()) upsertParents(parents)

        return LocalGroupKeyState(keyState = stamped, parents = parents)
    }
}
