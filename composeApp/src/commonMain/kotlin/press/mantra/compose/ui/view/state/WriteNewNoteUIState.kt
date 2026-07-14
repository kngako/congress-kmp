package press.mantra.compose.ui.view.state

import com.vitorpamplona.quartz.nip01Core.core.HexKey

abstract class WriteNewNoteUIState {
    data object Loading: WriteNewNoteUIState()

    data object Error: WriteNewNoteUIState()

    data class InputPrompt(
        val localProfile: press.mantra.compose.database.model.intermdiate.LocalProfileWithFollowing,
        val inReplyToNostrEvent: press.mantra.compose.database.model.intermdiate.LocalNostrEvent? = null,
        val quotedNostrEvent: press.mantra.compose.database.model.intermdiate.LocalNostrEvent? = null,
        val replayToId: HexKey? = null,
    ): WriteNewNoteUIState()

    data class ConfirmNote(
        val localProfile: press.mantra.compose.database.model.intermdiate.LocalProfileWithFollowing,
        val inReplyToNostrEvent: press.mantra.compose.database.model.intermdiate.LocalNostrEvent? = null,
        val quotedNostrEvent: press.mantra.compose.database.model.intermdiate.LocalNostrEvent? = null,
        val text: String,
    ) : WriteNewNoteUIState()

}