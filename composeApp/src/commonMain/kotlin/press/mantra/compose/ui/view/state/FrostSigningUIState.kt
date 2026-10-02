package press.mantra.compose.ui.view.state

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import press.mantra.compose.database.model.FrostSigningItem
import press.mantra.compose.database.model.FrostSigningSession
import press.mantra.compose.database.model.intermdiate.LocalChatRoom

sealed interface FrostSigningUIState {
    data class Loaded(
        val localChatRoom: LocalChatRoom,

        /** Null on the first emission, before the session has been collected. */
        val session: FrostSigningSession? = null,

        /**
         * The events the session signs, in the order its proposal fixed. Empty
         * on the first emission, and for a session whose proposal has not landed.
         */
        val items: List<FrostSigningItem> = emptyList(),

        /** Who has offered a nonce, so the screen can name who it is waiting on. */
        val offeredNonce: Set<HexKey> = emptySet(),

        /** Who has signed their part. */
        val signed: Set<HexKey> = emptySet(),

        /**
         * Names for the keys and ids the proposed events refer to.
         *
         * `ProposedEvent.summarize` is a pure function with no database -- two
         * call sites show these words and two copies of the mapping would drift
         * -- so whatever can be named is resolved here and handed in as a
         * lookup.
         *
         * It matters most for a membership, which carries two 64-character ids
         * and nothing else: unresolved, a member is asked to agree to "sign
         * a4f1be55d0c3". Empty is the honest default, and what a batch founding
         * a project sees for the project's own id, since nothing has applied it
         * yet.
         */
        val names: Map<HexKey, String> = emptyMap(),
    ): FrostSigningUIState

    data class Error(
        val message: String
    ): FrostSigningUIState

    data object Loading: FrostSigningUIState
}
