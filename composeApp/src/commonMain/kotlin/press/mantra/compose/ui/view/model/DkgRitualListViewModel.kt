package press.mantra.compose.ui.view.model

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
import kotlinx.coroutines.launch
import press.mantra.compose.database.model.DkgSession
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.database.model.types.DkgRitualStage
import press.mantra.compose.extensions.memberName
import press.mantra.compose.repository.ChatRepository
import press.mantra.compose.repository.DkgRepository
import press.mantra.compose.ui.view.state.DkgRitualListUIState

/**
 * Every key ceremony a room holds.
 *
 * A room routinely holds more than one: a parent's Marmot room hosts the
 * ceremony of every subgroup the group ever makes, alongside whatever it holds
 * of its own. Watching the whole list rather than the newest is the point --
 * which of them is waiting on the reader is a fact about each one, and the
 * newest is routinely the finished one.
 *
 * The counterpart to [ProposalListViewModel], and deliberately the same shape.
 */
class DkgRitualListViewModel(
    val chatRoomId: String,
    val activeUserPublicKey: HexKey,
    initialDkgRitualListUIState: DkgRitualListUIState,
    val chatRepository: ChatRepository,
    val dkgRepository: DkgRepository,
): ViewModel() {

    private val _dkgRitualListUIState = MutableStateFlow(initialDkgRitualListUIState)

    /**
     * A flow rather than a `mutableStateOf`: this is written from
     * `Dispatchers.IO`, and a `mutableStateOf` created in a view model's
     * constructor silently drops a write that lands during the composition that
     * created it. See [DkgRitualViewModel.dkgRitualUIState].
     */
    val dkgRitualListUIState: StateFlow<DkgRitualListUIState> =
        _dkgRitualListUIState.asStateFlow()

    private val logger = Logger.withTag(TAG)

    /**
     * Loads the room, then watches its ceremonies for as long as the screen
     * lives.
     *
     * Every one of them moves on messages arriving from other members, so a
     * screen that read the list once would go stale the moment somebody else
     * took their step -- which is exactly when a member is likely to be looking
     * at it.
     */
    fun initiate() {
        viewModelScope.launch(Dispatchers.IO) {
            val localChatRoom = chatRepository.getChatRoomByIdentifier(chatRoomId)
            if (localChatRoom == null) {
                _dkgRitualListUIState.value =
                    DkgRitualListUIState.Error("Couldn't find the group")
                return@launch
            }

            dkgRepository.observeSessionsForChatRoom(chatRoomId).collect { sessions ->
                logger.d("${sessions.size} key ceremonies in $chatRoomId")

                _dkgRitualListUIState.value = DkgRitualListUIState.Loaded(
                    localChatRoom = localChatRoom,
                    ceremonies = sessions.map { asCeremony(it, localChatRoom) }
                )
            }
        }
    }

    /**
     * One ceremony as a row.
     *
     * What it wants is asked of the repository rather than worked out here, so
     * this list, the transcript's standing notice and the ceremony screen cannot
     * disagree about which ceremonies still have something in them -- the same
     * rule the proposal list keeps.
     *
     * An abandoned ceremony is asked nothing. It can want nothing: there is no
     * step left to approve and no key to build a room from, and a row claiming
     * otherwise would send a member to a screen with nothing on it.
     */
    private suspend fun asCeremony(
        session: DkgSession,
        localChatRoom: LocalChatRoom
    ): DkgRitualListUIState.Ceremony {
        val live = session.takeIf { it.stage != DkgRitualStage.FAILED }

        return DkgRitualListUIState.Ceremony(
            session = session,
            coordinatorName = session.coordinatorPublicKey.memberName(
                members = localChatRoom.localParticipants,
                activeUserPublicKey = activeUserPublicKey
            ),
            pendingApproval = live?.let { dkgRepository.pendingApproval(it) },
            // Only of the member who opened it: creating the room is the
            // coordinator's job, and a row telling everybody else to do it would
            // be telling them to do something they cannot.
            roomAwaitingCreation = live
                ?.takeIf { it.isCoordinator() }
                ?.let { dkgRepository.roomAwaitingCreation(it) }
        )
    }

    companion object {
        private const val TAG = "DkgRitualListViewModel"

        fun factory(
            chatRoomId: String,
            activeUserPublicKey: HexKey,
            initialDkgRitualListUIState: DkgRitualListUIState = DkgRitualListUIState.Loading,
            chatRepository: ChatRepository,
            dkgRepository: DkgRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DkgRitualListViewModel(
                    chatRoomId = chatRoomId,
                    activeUserPublicKey = activeUserPublicKey,
                    initialDkgRitualListUIState = initialDkgRitualListUIState,
                    chatRepository = chatRepository,
                    dkgRepository = dkgRepository
                )
            }
        }
    }
}
