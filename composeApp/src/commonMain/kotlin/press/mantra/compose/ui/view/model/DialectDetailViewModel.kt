package press.mantra.compose.ui.view.model

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
import press.mantra.compose.repository.ChatRepository
import press.mantra.compose.repository.MantraRepository
import press.mantra.compose.ui.view.state.DialectDetailUIState

/**
 * A dialect, what is written in it, and whether it could have a room of its own.
 *
 * The two lists are the reason this screen exists: a dialect is a name, a
 * language code and a country, and the thing a member opens one to find out is
 * what the group has written in it. Both are read by the dialect's id alone --
 * see `MantraArtifactDao.getArtifactsByDialectId` for why no room is named
 * beside it.
 */
class DialectDetailViewModel(
    val dialectId: String,
    val chatRoomId: String,
    val activeUserPublicKey: HexKey,
    val relayHint: String?,
    initialDialectDetailUIState: DialectDetailUIState,
    val mantraRepository: MantraRepository,
    val chatRepository: ChatRepository,
): ViewModel() {

    var dialectDetailUIState: DialectDetailUIState by mutableStateOf(initialDialectDetailUIState)
        private set

    private val logger = Logger.withTag(TAG)

    fun initiateDialectDetail() {
        logger.d("initiateDialectDetail: $dialectId")
        viewModelScope.launch(Dispatchers.IO) {
            val dialect = mantraRepository.getDialect(dialectId)

            dialectDetailUIState = if (dialect == null) {
                // Error rather than an empty `Loaded`, because the two are
                // different answers: "this room has written nothing in Sesotho"
                // and "there is no such dialect here" would otherwise look
                // identical, and only one of them is worth retrying.
                DialectDetailUIState.Error("Couldn't find the dialect")
            } else {
                // The dialect's own room, not the route's. They are the same
                // room in every path that exists today, and reading it off the
                // row is what keeps that true if a route is ever constructed by
                // hand -- the parent of a subgroup made here must be the room
                // the dialect actually lives in.
                val room = chatRepository.getChatRoomByIdentifier(dialect.chatRoomId)

                DialectDetailUIState.Loaded(
                    dialect = dialect,
                    artifacts = mantraRepository.getArtifactsForDialect(dialectId),
                    translations = mantraRepository.getTranslationsForDialect(dialectId),
                    canAddSubgroup = room?.chatRoom?.mlsGroupState != null &&
                        chatRepository.canSign(dialect.chatRoomId),
                )
            }
        }
    }

    companion object {
        private const val TAG = "DialectDetailViewModel"

        fun factory(
            activeUserPublicKey: HexKey,
            dialectId: String,
            chatRoomId: String,
            relayHint: String?,
            initialDialectDetailUIState: DialectDetailUIState = DialectDetailUIState.Loading,
            mantraRepository: MantraRepository,
            chatRepository: ChatRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DialectDetailViewModel(
                    activeUserPublicKey = activeUserPublicKey,
                    dialectId = dialectId,
                    chatRoomId = chatRoomId,
                    relayHint = relayHint,
                    initialDialectDetailUIState = initialDialectDetailUIState,
                    mantraRepository = mantraRepository,
                    chatRepository = chatRepository,
                )
            }
        }
    }
}
