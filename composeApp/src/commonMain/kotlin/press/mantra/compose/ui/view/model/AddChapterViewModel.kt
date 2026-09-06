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
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import press.mantra.compose.database.model.MantraArtifactVersion
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.nostr.nip30303.ChapterEvent
import press.mantra.compose.repository.ChatRepository
import press.mantra.compose.repository.FrostSigningRepository
import press.mantra.compose.repository.MantraRepository
import press.mantra.compose.text.Markdown
import press.mantra.compose.ui.view.state.AddChapterUIState

class AddChapterViewModel(
    val artifactId: String,
    val chatRoomId: String,
    val activeUserPublicKey: HexKey,
    val relayHint: String?,
    initialAddChapterUIState: AddChapterUIState,
    val mantraRepository: MantraRepository,
    val chatRepository: ChatRepository,
    val frostSigningRepository: FrostSigningRepository,
): ViewModel() {

    var addChapterUIState: AddChapterUIState by mutableStateOf(initialAddChapterUIState)
        private set

    private val logger = Logger.withTag(TAG)

    val isActionPending: MutableState<Boolean> = mutableStateOf(false)

    fun initiateAddChapter() {
        viewModelScope.launch(Dispatchers.IO) {
            val artifact = mantraRepository.getArtifact(artifactId)
            val localChatRoom = chatRepository.getChatRoomByIdentifier(chatRoomId)

            addChapterUIState = when {
                artifact == null -> AddChapterUIState.Error("Couldn't find the artifact")
                localChatRoom == null -> AddChapterUIState.Error("Couldn't find the chat room")
                else -> AddChapterUIState.Loaded(
                    localChatRoom = localChatRoom,
                    artifact = artifact,
                    // Chapters attach to an artifact version; use the latest one.
                    artifactVersion = mantraRepository.getArtifactVersions(artifactId).firstOrNull(),
                    canSign = frostSigningRepository.canSign(chatRoomId),
                )
            }
        }
    }

    /**
     * Asks the group to sign a chapter into the artifact.
     *
     * The chapter is not created here and does not exist yet. What goes out is a
     * proposal to sign it, and the chapter -- with the chunks it splits into --
     * appears on every member's device at once, authored by this room's own key
     * rather than by whoever pasted the text, when enough members have signed.
     * That author is the room's id: signing runs at the path the room was
     * derived at, so a chapter says which group's artifact it belongs to simply
     * by being signed.
     *
     * That is the difference from submitting one. A submission says "I am
     * putting this in front of the group" and the group's only recourse
     * afterwards is social. A signature is the group saying it, and it takes a
     * quorum to say. The text everyone will translate from is the group's, so
     * the second is the honest one.
     */
    fun addChapter(
        localChatRoom: LocalChatRoom,
        artifactVersion: MantraArtifactVersion,
        nameField: TextFieldState,
        originalTextField: TextFieldState,
        onSuccess: (sessionId: String) -> Unit,
        onFailure: () -> Unit
    ) {
        val name = nameField.text.toString()
        val originalText = originalTextField.text.toString()

        if (name.isBlank() || originalText.isBlank()) {
            onFailure.invoke()
            return
        }

        // Guard against double submits from repeated FAB taps.
        if (isActionPending.value) return
        isActionPending.value = true

        viewModelScope.launch(Dispatchers.IO) {
            // The chunks are not proposed. They are split out of the chapter's
            // own text when the signed chapter is applied (see
            // MantraChunk.chunksOf), so one quorum buys the whole chapter rather
            // than one per paragraph, and every device splits the same text the
            // same way.
            val chapterEventTemplate = ChapterEvent.build(
                artifactVersionId = artifactVersion.id,
                name = name,
                originalText = originalText,
                // Where this chapter sits in the version, read now rather than
                // derived on arrival: the index is signed into the event, and a
                // device applying it cannot recount from a version other members
                // may have added to in a different order.
                index = mantraRepository.getChaptersForArtifactVersion(artifactVersion.id).size,
                wordCount = Markdown.wordCount(originalText),
                characterCount = Markdown.characterCount(originalText),
            )

            val session = runCatching {
                frostSigningRepository.proposeSigning(
                    localChatRoom = localChatRoom,
                    userPublicKey = activeUserPublicKey,
                    kind = chapterEventTemplate.kind,
                    tags = chapterEventTemplate.tags,
                    content = chapterEventTemplate.content,
                )
            }.onFailure { error ->
                logger.e("Failed to propose a chapter for signing", error)
            }.getOrNull()

            if (session != null) {
                nameField.clearText()
                originalTextField.clearText()

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
        private const val TAG = "AddChapterViewModel"

        fun factory(
            activeUserPublicKey: HexKey,
            artifactId: String,
            chatRoomId: String,
            relayHint: String?,
            initialAddChapterUIState: AddChapterUIState = AddChapterUIState.Loading,
            mantraRepository: MantraRepository,
            chatRepository: ChatRepository,
            frostSigningRepository: FrostSigningRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AddChapterViewModel(
                    activeUserPublicKey = activeUserPublicKey,
                    artifactId = artifactId,
                    chatRoomId = chatRoomId,
                    relayHint = relayHint,
                    initialAddChapterUIState = initialAddChapterUIState,
                    mantraRepository = mantraRepository,
                    chatRepository = chatRepository,
                    frostSigningRepository = frostSigningRepository,
                )
            }
        }
    }
}
