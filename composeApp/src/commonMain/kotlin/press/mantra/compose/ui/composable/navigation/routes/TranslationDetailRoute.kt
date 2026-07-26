package press.mantra.compose.ui.composable.navigation.routes

import kotlinx.serialization.Serializable

@Serializable
data class TranslationDetailRoute(
    val activeUserPublicKey: String,
    val translationArtifactVersionId: String,
    val chatRoomId: String,
    val relayHint: String?
): Route()
