package press.mantra.compose.ui.view.state

import press.mantra.compose.database.model.intermdiate.LocalChatRoom

sealed interface SelectAdoptingGroupUIState {
    data class Loaded(
        val childRoom: LocalChatRoom,
        /**
         * The groups this profile could ask, in room order.
         *
         * **The profile's own rooms, and nothing else is possible.** A
         * certificate is proposed *inside* the adopting group, so a candidate has
         * to be a room the key in the chair can already act in. Empty is a fact
         * about this member rather than a failure -- a group cannot be offered as
         * a parent by somebody who is not in it, and the remedy is a person
         * rather than a button.
         */
        val candidates: List<LocalChatRoom> = emptyList(),
    ): SelectAdoptingGroupUIState

    data class Error(val message: String): SelectAdoptingGroupUIState

    data object Loading: SelectAdoptingGroupUIState
}
