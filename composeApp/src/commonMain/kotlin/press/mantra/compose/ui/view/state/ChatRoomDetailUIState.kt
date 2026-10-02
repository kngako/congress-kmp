package press.mantra.compose.ui.view.state

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import press.mantra.compose.database.model.GroupKeyState
import press.mantra.compose.managers.SubgroupManager
import press.mantra.compose.database.model.GroupSignedEvent
import press.mantra.compose.database.model.MantraArtifact
import press.mantra.compose.database.model.MantraDialect
import press.mantra.compose.database.model.MantraProject
import press.mantra.compose.database.model.intermdiate.LocalChatRoom

sealed interface ChatRoomDetailUIState {
    data class Loaded(
        val localChatRoom: LocalChatRoom,
        val artifacts: List<MantraArtifact> = emptyList(),
        val dialects: List<MantraDialect> = emptyList(),

        /** What the room's library is being used for. */
        val projects: List<MantraProject> = emptyList(),
        /**
         * What the group has said this room signs with, or null while it has
         * said nothing.
         *
         * Sits above everything else on the screen because it is the fact the
         * rest of the room's signed work stands on: which key the room signs as
         * decides whether anything it has signed will be believed.
         */
        val groupKeyState: GroupKeyState? = null,
        /**
         * The event the group signed to say it, as the group signed it.
         *
         * Held beside [groupKeyState] rather than derived from it, because a
         * state is this device's reading and this is the statement -- with the
         * signature on it, which is the part worth copying somewhere it can be
         * checked. Null on a device that holds the reading and not the event.
         */
        val signedGroupKeyStateEvent: GroupSignedEvent? = null,
        /**
         * The groups this one has certified as its children.
         *
         * Off the parent's own signed certificates, so it lists a subgroup this
         * device holds no room for -- which is the normal position of a member
         * who is not in it, and of everybody between the certificate being
         * signed and the room being created.
         */
        val subgroups: List<SubgroupManager.Subgroup> = emptyList(),
        /**
         * The groups this room is a subgroup of, empty when it is nobody's
         * child.
         *
         * Verified: it comes off the room's key state, which only carries a
         * parent whose certificate was checked. A member welcomed in after the
         * founding holds no state and so sees nothing here, even in a room that
         * really is a subgroup.
         *
         * Each carries the parent's room where this device holds one, so the
         * card can name the group rather than only identify it.
         */
        val parents: List<SubgroupManager.Parent> = emptyList(),
        /**
         * Whether this device could open a subgroup here at all.
         *
         * Two conditions and both are about capability rather than permission.
         * The room has to be a Marmot one, since a NIP-17 room has no admins to
         * be a parent's. And this device has to hold a share of the group's key,
         * because certifying a child is a signature by the parent and
         * `FrostSigningManager.proposeSigningBatch` throws for a device that
         * cannot make one -- throwing at a button is not a UI.
         *
         * The rest of the refusals are the picker's, where they can name who is
         * missing and what to do about it.
         */
        val canAddSubgroup: Boolean = false,
        /**
         * Groups that have offered to adopt this one and are not parents yet.
         *
         * An offer is a certificate signed in the *other* group's room, so only a
         * member of that group holds one -- which is the overlap an adoption
         * needs, showing up here as a list that is empty for everybody else.
         *
         * Each is verified: the signature was checked against the group it names,
         * which needs no lookup. What is unverified is nothing; what is
         * *unsettled* is everything, because an offer is not a lineage until this
         * group's own quorum says so.
         */
        val adoptionOffers: List<HexKey> = emptyList(),
        /**
         * Whether this device could put a parent to this group at all.
         *
         * The room has to be a Marmot one with a key state -- the certificate
         * carries the child's threshold key and the acceptance re-announces that
         * state -- and this device has to hold a share, because both halves are
         * signatures by this group. The rest of the refusals are the picker's,
         * where they can name which group and why.
         */
        val canAddParent: Boolean = false,
    ): ChatRoomDetailUIState

    data class Error(
        val message: String
    ): ChatRoomDetailUIState
    data object Loading: ChatRoomDetailUIState
}

