package press.mantra.compose.ui.view.model

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import press.mantra.compose.repository.ChatRepository
import press.mantra.compose.repository.NostrRepository
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.nostr.nip30303.ArtifactEvent
import press.mantra.compose.repository.FrostSigningRepository
import press.mantra.compose.repository.MantraRepository
import press.mantra.compose.ui.view.state.AddArtifactUIState

class AddArtifactViewModel(
    val chatRoomId: String,
    val activeUserPublicKey: HexKey,
    val relayHint: String?,
    initialAddArtifactUIState: AddArtifactUIState,
    val nostrRepository: NostrRepository,
    val chatRepository: ChatRepository,
    val mantraRepository: MantraRepository,
    val frostSigningRepository: FrostSigningRepository,
): ViewModel() {

    var addArtifactUIState: AddArtifactUIState by mutableStateOf(initialAddArtifactUIState)
        private set

    private val logger = Logger.withTag(TAG)

    val isActionPending: MutableState<Boolean> = mutableStateOf(false)

    fun initiateChatRoomDetail() {
        logger.d("compressed (most likely chat room): $chatRoomId")
        // Get ChatRoomById
        viewModelScope.launch(Dispatchers.IO) {
            val localChatRoom = chatRepository.getChatRoomByIdentifier(chatRoomId)

            addArtifactUIState = if (localChatRoom == null) {
                // Initiate new chat... and navigate to new chat...
                AddArtifactUIState.Error("Couldn't find the chat room")
            } else {
                AddArtifactUIState.Loaded(
                    localChatRoom = localChatRoom,
                    dialects = mantraRepository.getDialects(localChatRoom.chatRoom.id),
                    canSign = frostSigningRepository.canSign(chatRoomId),
                )
            }
        }
    }

    /**
     * Asks the group to sign an artifact into its library.
     *
     * The artifact is not created here and does not exist yet. What goes out is
     * a proposal to sign it, and the artifact appears -- on every member's
     * device at once, authored by the group's shared key rather than by whoever
     * typed it -- when enough members have signed.
     *
     * That is the difference from submitting one. A submission says "I am
     * putting this in front of the group" and the group's only recourse
     * afterwards is social. A signature is the group saying it, and it takes a
     * quorum to say. A library is the group's, so the second is the honest one.
     */
    fun addArtifact(
        localChatRoom: LocalChatRoom,
        nameField: TextFieldState,
        urlField: TextFieldState,
        versionLabelField: TextFieldState,
        dialectId: HexKey?,
        visibility: String = MantraRepository.DEFAULT_VISIBILITY,
        license: String = MantraRepository.DEFAULT_LICENSE,
        onSuccess: (sessionId: String) -> Unit,
        onFailure: () -> Unit
    ) {
        val name = nameField.text.toString()
        val url = urlField.text.toString()
        val versionLabel = versionLabelField.text.toString()

        // An artifact requires an existing source dialect; they are signed into
        // existence from the group detail screen, not here.
        if (name.isBlank() || url.isBlank() || versionLabel.isBlank() || dialectId.isNullOrBlank()) {
            onFailure.invoke()
            return
        }

        // Guard against double submits from repeated FAB taps.
        if (isActionPending.value) return
        isActionPending.value = true

        viewModelScope.launch(Dispatchers.IO) {
            // The version label rides on the artifact rather than following it as
            // a second event. The group signs the artifact; a first version
            // proposed on its own would cost a second quorum for one form, and
            // every device derives the same first version from what was signed.
            val artifactEventTemplate = ArtifactEvent.build(
                name = name,
                url = url,
                visibility = visibility,
                license = license,
                dialectId = dialectId,
                versionLabel = versionLabel,
            )

            val session = runCatching {
                frostSigningRepository.proposeSigning(
                    localChatRoom = localChatRoom,
                    userPublicKey = activeUserPublicKey,
                    kind = artifactEventTemplate.kind,
                    tags = artifactEventTemplate.tags,
                    content = artifactEventTemplate.content,
                )
            }.onFailure { error ->
                logger.e("Failed to propose an artifact for signing", error)
            }.getOrNull()

            if (session != null) {
                nameField.clearText()
                urlField.clearText()
                versionLabelField.clearText()

                viewModelScope.launch(Dispatchers.Main) {
                    onSuccess.invoke(session.id)
                }
            } else {
                viewModelScope.launch(Dispatchers.Main) {
                    onFailure.invoke()
                }
            }

            isActionPending.value = false
        }
    }

    companion object {
        private const val TAG = "AddArtifactViewModel"

        fun factory(
            activeUserPublicKey: HexKey,
            chatRoomId: String,
            relayHint: String?,
            initialAddArtifactUIState: AddArtifactUIState = AddArtifactUIState.Loading,
            nostrRepository: NostrRepository,
            chatRepository: ChatRepository,
            mantraRepository: MantraRepository,
            frostSigningRepository: FrostSigningRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AddArtifactViewModel(
                    activeUserPublicKey = activeUserPublicKey,
                    chatRoomId = chatRoomId,
                    relayHint = relayHint,
                    initialAddArtifactUIState = initialAddArtifactUIState,
                    nostrRepository = nostrRepository,
                    chatRepository = chatRepository,
                    mantraRepository = mantraRepository,
                    frostSigningRepository = frostSigningRepository
                )
            }
        }
    }
}
