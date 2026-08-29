package press.mantra.compose.database.model.types

import kotlinx.serialization.Serializable

/**
 * How a chat room is governed.
 *
 * The choice is stamped into the epoch-0 `MarmotGroupData.admin_pubkeys` list,
 * which is what `MarmotInboundManager.processGroupMembershipChanges` reads to
 * flag participants as admins on every side of the group.
 */
@Serializable
enum class ChatRoomType {
    /** Only the member who created the room administers it. */
    CONVENIENT,

    /**
     * Every member administers the room, and a change needs a threshold of them
     * to approve it.
     */
    ROBUST;

    companion object {
        /**
         * Smallest group [ROBUST] is offered for. Below three, a majority is not a
         * meaningful check: two admins means every change needs both of them, and
         * one means the creator is deciding alone — which is [CONVENIENT] already.
         */
        const val MINIMUM_ROBUST_GROUP_SIZE = 3

        /**
         * How many of [adminCount] admins have to approve a change — a simple
         * majority, so no half of the group can move without the other.
         */
        fun approvalThreshold(adminCount: Int): Int = adminCount / 2 + 1

        /** Whether a group of [memberCount] people can be run as [ROBUST]. */
        fun isRobustAvailable(memberCount: Int): Boolean = memberCount >= MINIMUM_ROBUST_GROUP_SIZE
    }
}
