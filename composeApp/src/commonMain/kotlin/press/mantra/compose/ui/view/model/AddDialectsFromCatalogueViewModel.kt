package press.mantra.compose.ui.view.model

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import press.mantra.compose.language.AfricanLanguage
import press.mantra.compose.managers.DialectScaffold
import press.mantra.compose.repository.ChatRepository
import press.mantra.compose.repository.FrostSigningRepository
import press.mantra.compose.repository.MantraRepository
import press.mantra.compose.ui.view.state.AddDialectsFromCatalogueUIState
import press.mantra.compose.ui.view.state.CatalogueRow

/**
 * Picking dialects out of the catalogue rather than typing one in.
 *
 * Reads what the room holds and what it is already signing, and offers the rest.
 * The second half of that is the part worth having: without it two admins open
 * this screen, both see the same forty missing and both propose, and the room
 * gets eighty dialects -- forty of them duplicates nothing will ever merge,
 * because nothing in the schema makes a dialect unique.
 */
class AddDialectsFromCatalogueViewModel(
    val chatRoomId: String,
    val activeUserPublicKey: HexKey,
    val relayHint: String?,
    initialUIState: AddDialectsFromCatalogueUIState,
    val chatRepository: ChatRepository,
    val mantraRepository: MantraRepository,
    val frostSigningRepository: FrostSigningRepository,
): ViewModel() {

    /**
     * A `StateFlow` rather than the `mutableStateOf` the neighbouring view models
     * use, and not for consistency's sake.
     *
     * Those write their state once. This one is written from `Dispatchers.IO` on
     * the way in *and* again from a collector on the room's signing sessions,
     * which is the case where a `mutableStateOf` in a view model drops a write
     * made off the main thread during the first composition. `update { }` over a
     * `MutableStateFlow` is the shape that does not.
     */
    private val _uiState = MutableStateFlow(initialUIState)
    val uiState: StateFlow<AddDialectsFromCatalogueUIState> = _uiState.asStateFlow()

    private val logger = Logger.withTag(TAG)

    val isActionPending: MutableState<Boolean> = mutableStateOf(false)

    /**
     * What is ticked, by the only thing that identifies a dialect.
     *
     * Keys rather than entries so that a row arriving from a refresh is the same
     * selection as the one that was ticked, and a `mutableStateListOf` rather
     * than a field of the UI state because the UI read-modify-writes it on every
     * tap -- see the note on [_uiState].
     */
    val selected = mutableStateListOf<DialectScaffold.Key>()

    val query: MutableState<String> = mutableStateOf("")

    fun initiate() {
        viewModelScope.launch(Dispatchers.IO) {
            val localChatRoom = chatRepository.getChatRoomByIdentifier(chatRoomId)

            if (localChatRoom == null) {
                _uiState.value = AddDialectsFromCatalogueUIState.Error("Couldn't find this group")
                return@launch
            }

            _uiState.value = AddDialectsFromCatalogueUIState.Loaded(
                localChatRoom = localChatRoom,
                canSign = frostSigningRepository.canSign(chatRoomId),
            )

            // The room's sessions drive the refresh, and the rows are re-read
            // with them rather than observed separately. A wave landing changes
            // both at once -- its session completes in the same breath as its
            // dialects become rows -- so one emission covers them, and the
            // dialect DAO has no flow to observe anyway.
            //
            // What this does not catch is a dialect arriving with no session of
            // this room behind it: a chronicle page, or inbound work from
            // another device. Those are picked up on the way in rather than
            // live, which is the right trade for a screen somebody opens,
            // ticks and leaves.
            frostSigningRepository.observeSessionsForChatRoom(chatRoomId).collect { sessions ->
                val pending = DialectScaffold.pendingIn(
                    sessions = sessions,
                    unsignedEvents = frostSigningRepository::unsignedEvents,
                )

                val rows = CatalogueRow.rowsOf(
                    existing = mantraRepository.getDialects(chatRoomId),
                    pending = pending,
                )

                _uiState.update { state ->
                    when (state) {
                        is AddDialectsFromCatalogueUIState.Loaded -> state.copy(rows = rows)
                        else -> state
                    }
                }

                // Anything that stopped being missing while this screen was open
                // -- another admin proposed it, or a wave landed -- comes off the
                // selection. Leaving it ticked would propose a dialect the room
                // already has, and the two would not even collide.
                val stillMissing = rows
                    .filter { it.standing == CatalogueRow.Standing.MISSING }
                    .mapTo(mutableSetOf()) { DialectScaffold.keyOf(it.language) }

                selected.retainAll(stillMissing)
            }
        }
    }

    /** Ticks or unticks [language]. A row that is not missing is not ours to tick. */
    fun toggle(language: AfricanLanguage) {
        if (isActionPending.value) return

        val loaded = _uiState.value as? AddDialectsFromCatalogueUIState.Loaded ?: return
        val key = DialectScaffold.keyOf(language)

        if (loaded.rows.none { it.standing == CatalogueRow.Standing.MISSING && DialectScaffold.keyOf(it.language) == key }) {
            return
        }

        if (!selected.remove(key)) selected.add(key)
    }

    fun isSelected(language: AfricanLanguage): Boolean =
        DialectScaffold.keyOf(language) in selected

    /**
     * Ticks everything the room is missing -- the whole convenience this screen
     * is named for.
     *
     * Everything missing rather than everything shown: a member who has typed
     * "so" into the search and taps this means the catalogue, not the three rows
     * in front of them, and a selection that silently depended on a filter would
     * be the harder of the two to notice.
     */
    fun selectEverythingMissing() {
        if (isActionPending.value) return

        val loaded = _uiState.value as? AddDialectsFromCatalogueUIState.Loaded ?: return

        selected.clear()
        selected.addAll(loaded.missing.map(DialectScaffold::keyOf))
    }

    fun clearSelection() {
        if (isActionPending.value) return

        selected.clear()
    }

    fun setQuery(value: String) {
        query.value = value
    }

    /** What is ticked, as catalogue entries in catalogue order. */
    fun selectedLanguages(): List<AfricanLanguage> {
        val loaded = _uiState.value as? AddDialectsFromCatalogueUIState.Loaded ?: return emptyList()

        return loaded.missing.filter { DialectScaffold.keyOf(it) in selected }
    }

    companion object {
        private const val TAG = "AddDialectsFromCatalogueViewModel"

        fun factory(
            activeUserPublicKey: HexKey,
            chatRoomId: String,
            relayHint: String?,
            initialUIState: AddDialectsFromCatalogueUIState = AddDialectsFromCatalogueUIState.Loading,
            chatRepository: ChatRepository,
            mantraRepository: MantraRepository,
            frostSigningRepository: FrostSigningRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AddDialectsFromCatalogueViewModel(
                    activeUserPublicKey = activeUserPublicKey,
                    chatRoomId = chatRoomId,
                    relayHint = relayHint,
                    initialUIState = initialUIState,
                    chatRepository = chatRepository,
                    mantraRepository = mantraRepository,
                    frostSigningRepository = frostSigningRepository,
                )
            }
        }
    }
}
