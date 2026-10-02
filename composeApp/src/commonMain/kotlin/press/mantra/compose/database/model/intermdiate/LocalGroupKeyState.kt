package press.mantra.compose.database.model.intermdiate

import androidx.room3.Embedded
import androidx.room3.Relation
import com.vitorpamplona.quartz.nip01Core.core.Event
import press.mantra.compose.database.model.GroupKeyState
import press.mantra.compose.database.model.GroupKeyStateParent
import press.mantra.compose.nostr.subgroup.SubgroupLineage
import press.mantra.compose.nostr.subgroup.SubgroupParentage

/**
 * A room's key state with every group that has certified it as a child.
 *
 * The two always travel together, because a state and the lineage it claims are
 * one signed statement: `GroupKeyStateManager.stateFrom` either believes all of
 * it or drops all of it, so a reader holding the state without the parents would
 * be holding half of something that has no halves.
 *
 * Also the shape `stateFrom` returns. It is a Room read model -- `@Embedded`
 * over `@Relation`, like `LocalChatRoom` -- and a plain data class besides, so
 * the pure verifier can build one with no database anywhere near it.
 */
data class LocalGroupKeyState(
    @Embedded
    val keyState: GroupKeyState,

    @Relation(
        entity = GroupKeyStateParent::class,
        parentColumns = ["chatRoomId"],
        entityColumns = ["chatRoomId"],
    )
    val parents: List<GroupKeyStateParent> = emptyList(),
) {
    /**
     * The lineage these rows amount to, or null for a room that is nobody's
     * child.
     *
     * Null also where the rows cannot be read back as a lineage -- a certificate
     * whose JSON no longer parses, or a repeat the store should have made
     * impossible. Both are the same answer here, and it is the safe one: a
     * lineage that cannot be stated is not half-stated, for the reason
     * `stateFrom` drops a whole state rather than keeping a parentage it could
     * not check.
     *
     * Rebuilt rather than cached, so the canonical order and the no-repeats rule
     * come from `SubgroupLineage` on every read instead of from whatever order
     * the rows came back in.
     */
    fun lineage(): SubgroupLineage? = SubgroupLineage.of(
        parents.mapNotNull { parent ->
            Event.fromJsonOrNull(parent.birthCertificateJson)?.let { certificate ->
                SubgroupParentage(
                    parentChatRoomId = parent.parentChatRoomId,
                    certificate = certificate
                )
            }
        }.takeIf { it.size == parents.size } ?: return null
    )

    /** The groups that have certified this room, in canonical order. */
    fun parentChatRoomIds(): List<String> = lineage()?.parentChatRoomIds.orEmpty()
}
