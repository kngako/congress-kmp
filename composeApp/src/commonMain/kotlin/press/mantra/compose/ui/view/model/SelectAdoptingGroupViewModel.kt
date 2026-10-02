package press.mantra.compose.ui.view.model

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import press.mantra.compose.repository.ChatRepository
import press.mantra.compose.ui.view.state.SelectAdoptingGroupUIState

/**
 * Which group to ask to adopt this one.
 *
 * The first of an adoption's two signings. Confirming proposes a birth
 * certificate in the **chosen** group's room, and that is all it does: the
 * certificate is an **offer**, and this room is not that group's subgroup until
 * its own quorum re-announces its key state naming them. So this screen hands
 * back to the room it came from rather than anywhere new -- the acceptance is a
 * card there, and the answer belongs to whoever opens it.
 *
 * ### The candidates cannot be anything but this profile's own rooms
 *
 * A certificate is proposed *inside* the adopting group, so a candidate has to be
 * a room the key in the chair can act in: a Marmot room this profile administers
 * and holds a share of, which is not this room, is not already a parent, has not
 * already offered, and is not already beneath this one. Every one of those is
 * `SubgroupManager.refuseAdoption`'s, run through that function rather than
 * through a copy of its rules, so the screen and the manager cannot drift.
 *
 * An empty list is therefore a fact about this member rather than a failure, and
 * the screen says so: a group cannot be offered as a parent by somebody who is
 * not in it, and the remedy is a person rather than a button.
 */
class SelectAdoptingGroupViewModel(
    val activeUserPublicKey: HexKey,
    val chatRoomId: String,
    initialUIState: SelectAdoptingGroupUIState,
    val chatRepository: ChatRepository,
): ViewModel() {

    var uiState: SelectAdoptingGroupUIState by mutableStateOf(initialUIState)
        private set

    private val logger = Logger.withTag(TAG)

    val isActionPending: MutableState<Boolean> = mutableStateOf(false)

    fun initiate() {
        viewModelScope.launch(Dispatchers.IO) {
            val childRoom = chatRepository.getChatRoomByIdentifier(chatRoomId)
            if (childRoom == null) {
                uiState = SelectAdoptingGroupUIState.Error("Couldn't load this group")
                return@launch
            }

            uiState = SelectAdoptingGroupUIState.Loaded(
                childRoom = childRoom,
                candidates = chatRepository.adoptingCandidatesFor(
                    chatRoomId = chatRoomId,
                    userPublicKey = activeUserPublicKey
                )
            )
        }
    }

    /**
     * Asks [adoptingChatRoomId]'s quorum to certify this room as its child, then
     * hands back.
     *
     * Checked again here rather than trusted from the list, because the screen
     * may have been open a while and a screen not drawing something is not a
     * guard. The manager checks a third time; this one exists to say *why* rather
     * than to throw.
     */
    fun offerTo(adoptingChatRoomId: String, onDone: (String?) -> Unit) {
        if (isActionPending.value) return
        isActionPending.value = true

        viewModelScope.launch(Dispatchers.IO) {
            val refusal = chatRepository.refuseAdoption(
                chatRoomId = chatRoomId,
                adoptingChatRoomId = adoptingChatRoomId,
                userPublicKey = activeUserPublicKey
            )

            if (refusal != null) {
                isActionPending.value = false
                finish(refusal, onDone)
                return@launch
            }

            val proposed = chatRepository.proposeAdoption(
                chatRoomId = chatRoomId,
                adoptingChatRoomId = adoptingChatRoomId,
                userPublicKey = activeUserPublicKey
            )

            isActionPending.value = false

            if (!proposed) {
                logger.e("Failed to ask $adoptingChatRoomId to adopt $chatRoomId")
                finish("Couldn't ask that group to adopt this one. Please try again.", onDone)
                return@launch
            }

            // Reload before handing back, so the row that was just asked leaves
            // the list: a certificate now stands for the pair and
            // `refuseAdoption` says so. That is the confirmation, and it is a
            // better one than a message -- the list is the state.
            initiate()
            finish(null, onDone)
        }
    }

    /**
     * Hands [message] back **on the main thread**.
     *
     * The work runs on `Dispatchers.IO` and a caller's callback routinely touches
     * things that may not be touched from there -- the first version of this
     * screen called `popBackStack()` straight out of the IO coroutine and crashed
     * on `Method setCurrentState must be called on the main thread`. The
     * dispatcher is this function's business rather than every caller's, which is
     * the shape `AddArtifactViewModel` and `SelectSubgroupAdminsViewModel` already
     * use for the same handoff.
     */
    private suspend fun finish(message: String?, onDone: (String?) -> Unit) {
        withContext(Dispatchers.Main) { onDone(message) }
    }

    companion object {
        private const val TAG = "SelectAdoptingGroupViewModel"

        fun factory(
            activeUserPublicKey: HexKey,
            chatRoomId: String,
            initialUIState: SelectAdoptingGroupUIState = SelectAdoptingGroupUIState.Loading,
            chatRepository: ChatRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SelectAdoptingGroupViewModel(
                    activeUserPublicKey = activeUserPublicKey,
                    chatRoomId = chatRoomId,
                    initialUIState = initialUIState,
                    chatRepository = chatRepository
                )
            }
        }
    }
}
