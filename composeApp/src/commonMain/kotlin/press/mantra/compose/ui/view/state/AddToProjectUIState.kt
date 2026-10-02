package press.mantra.compose.ui.view.state

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import press.mantra.compose.database.model.MantraProject
import press.mantra.compose.database.model.intermdiate.LocalChatRoom

sealed interface AddToProjectUIState {
    /**
     * [candidates] is whatever is not already in the project: the room's source
     * versions or its translations, depending on which half this screen is for.
     *
     * Already-included work is filtered out rather than shown and refused. A
     * membership that duplicates one the group has already signed is a second
     * event with a different id, so nothing would stop it -- it would simply
     * cost a quorum to say what the group has already said.
     */
    data class Loaded(
        val localChatRoom: LocalChatRoom,
        val project: MantraProject,
        val candidates: List<Candidate> = emptyList(),
        val canSign: Boolean = false,
    ): AddToProjectUIState

    /** One thing that could be added, named the way the picker shows it. */
    data class Candidate(val id: HexKey, val label: String, val detail: String?)

    data class Error(
        val message: String
    ): AddToProjectUIState

    data object Loading: AddToProjectUIState
}
