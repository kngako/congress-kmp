package press.mantra.compose.ui.composable

import press.mantra.compose.database.model.ChatRoom
import press.mantra.compose.database.model.intermdiate.LocalChatRoom

/**
 * A group room to compose the group's screen against.
 *
 * The fork's fixture of this name also carries the signed work the group's screen
 * showed there -- a profile, relay lists, posts, curated schemas and entries -- which
 * Mantra took out with lines B, D and H (docs/curated-to-mantra.md). What is left is
 * the room itself, under the same names, so that a test written upstream against the
 * room alone compiles here unchanged; `ChatRoomMemberRowJvmTest` is the first.
 */
internal object GroupSignedWorkFixtures {

    val chatRoomId = "d4c3b2a1".repeat(8)

    val activeUserPublicKey = "a".repeat(64)

    fun localChatRoom(mlsGroupState: String? = "state") = LocalChatRoom(
        chatRoom = ChatRoom(
            id = chatRoomId,
            userPublicKey = activeUserPublicKey,
            subject = "Translation room",
            description = "A group translating hard books.",
            initialGiftWrapPayloadId = "sdfaer",
            mlsGroupState = mlsGroupState
        )
    )
}
