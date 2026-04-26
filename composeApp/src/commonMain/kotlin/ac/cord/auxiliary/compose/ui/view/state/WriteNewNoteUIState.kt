package ac.cord.auxiliary.compose.ui.view.state

import ac.cord.auxiliary.compose.database.model.intermdiate.LocalNostrEvent
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalProfile
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalProfileWithFollowing
import com.vitorpamplona.quartz.nip01Core.core.HexKey

abstract class WriteNewNoteUIState {
    data object Loading: WriteNewNoteUIState()

    data object Error: WriteNewNoteUIState()

    data class InputPrompt(
        val localProfile: LocalProfileWithFollowing,
        val inReplyToNostrEvent: LocalNostrEvent? = null,
        val quotedNostrEvent: LocalNostrEvent? = null,
        val replayToId: HexKey? = null,
    ): WriteNewNoteUIState()

    data class ConfirmNote(
        val localProfile: LocalProfileWithFollowing,
        val inReplyToNostrEvent: LocalNostrEvent? = null,
        val quotedNostrEvent: LocalNostrEvent? = null,
        val text: String,
    ) : WriteNewNoteUIState()

}