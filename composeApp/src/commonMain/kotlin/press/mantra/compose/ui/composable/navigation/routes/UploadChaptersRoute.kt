package press.mantra.compose.ui.composable.navigation.routes

import kotlinx.serialization.Serializable

/**
 * Several files at once, each becoming a chapter the group is asked to sign.
 *
 * The same arguments as [AddChapterRoute], because it is the same act at a
 * different scale -- and deliberately a separate destination rather than a mode
 * on that one. The add form is a text field and one proposal; this is a review
 * list and N of them, with a different thing to say before it is used.
 */
@Serializable
data class UploadChaptersRoute(
    val activeUserPublicKey: String,
    val artifactId: String,
    val chatRoomId: String,
    val relayHint: String?
): Route()
