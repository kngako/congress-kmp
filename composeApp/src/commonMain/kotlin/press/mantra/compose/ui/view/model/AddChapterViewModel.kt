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
import press.mantra.compose.managers.ChapterIndexing
import press.mantra.compose.managers.ChapterScaffolding
import press.mantra.compose.managers.FrostSigningManager
import press.mantra.compose.nostr.nip30303.ChapterEvent
import press.mantra.compose.nostr.nip30303.ChapterProposal
import press.mantra.compose.nostr.nip30303.ChunkEvent
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
     * Asks the group to sign a chapter, and every chunk it splits into, in one
     * session.
     *
     * Nothing is created here. What goes out is a proposal to sign the lot, and
     * the chapter and its chunks appear on every member's device at once,
     * authored by this room's own key rather than by whoever pasted the text,
     * when enough members have signed. That author is the room's id: signing
     * runs at the path the room was derived at, so a chapter says which group's
     * artifact it belongs to simply by being signed.
     *
     * That is the difference from submitting one. A submission says "I am
     * putting this in front of the group" and the group's only recourse
     * afterwards is social. A signature is the group saying it, and it takes a
     * quorum to say. The text everyone will translate from is the group's, so
     * the second is the honest one.
     *
     * The chunks travel as their own events rather than being split back out of
     * the signed chapter on each device, so that each one carries the group's
     * signature over its own text -- a translation is of a chunk, and a chunk
     * that can be checked on its own is worth more than one that can only be
     * checked by re-deriving it. What it costs is [MAX_CHUNKS_PER_CHAPTER]: the
     * batch is capped, the chapter takes one of its places, and a chapter with
     * more paragraphs than the rest cannot be proposed at all.
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
        val chunks = Markdown.splitChunks(originalText)

        // Both caps are the group's, not this screen's, and proposing past
        // either throws rather than failing softly. The form says so as it is
        // typed; these are the checks that have to hold when it does not.
        //
        // Two caps rather than one because they bound different things: how many
        // events a session signs, and how many bytes the proposal carrying them
        // weighs. A chapter of sixty-three one-word paragraphs passes the second
        // and fails the first; one of three very long ones does the reverse.
        if (name.isBlank() ||
            chunks.isEmpty() ||
            chunks.size > MAX_CHUNKS_PER_CHAPTER ||
            !ChapterProposal.fits(originalText, chunks)
        ) {
            onFailure.invoke()
            return
        }

        // Guard against double submits from repeated FAB taps.
        if (isActionPending.value) return
        isActionPending.value = true

        viewModelScope.launch(Dispatchers.IO) {
            // Where this chapter sits in the version, read now rather than
            // derived on arrival: the index is signed into the event, and a
            // device applying it cannot recount from a version other members
            // may have added to in a different order. The scaffolding proposed
            // below is placed at the same one, so a translation chapter agrees
            // with the chapter it stands for.
            //
            // Counting the room's open proposals as well as its rows, because a
            // chapter proposed a second ago is not a row yet and reading the
            // table alone would hand it this chapter's number -- see
            // [ChapterIndexing.nextIndex].
            val chapterIndex = ChapterIndexing.nextIndex(
                mantraRepository = mantraRepository,
                frostSigningRepository = frostSigningRepository,
                chatRoomId = chatRoomId,
                artifactVersionId = artifactVersion.id,
            )

            val chapterEventTemplate = ChapterEvent.build(
                artifactVersionId = artifactVersion.id,
                name = name,
                originalText = originalText,
                index = chapterIndex,
                wordCount = Markdown.wordCount(originalText),
                characterCount = Markdown.characterCount(originalText),
            )

            val session = runCatching {
                frostSigningRepository.proposeSigningBatch(
                    localChatRoom = localChatRoom,
                    userPublicKey = activeUserPublicKey,
                    lead = chapterEventTemplate,
                    // A chunk names the chapter it belongs to, and that id is a
                    // hash over the group's key at the room's path -- neither of
                    // which this screen knows or should. The chapter comes back
                    // built, and the chunks are cut from the text it carries.
                    dependents = ChunkEvent::splitOf
                )
            }.onFailure { error ->
                logger.e("Failed to propose a chapter for signing", error)
            }.getOrNull()

            if (session != null) {
                // Before navigating, not after: the route this screen sits on
                // is popped on success, which clears this view model and takes
                // `viewModelScope` with it. Scaffolding launched afterwards
                // would be cancelled somewhere in the middle.
                scaffoldIntoTranslations(
                    localChatRoom = localChatRoom,
                    artifactVersion = artifactVersion,
                    chapterIndex = chapterIndex,
                    chapterSessionId = session.id,
                )

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

    /**
     * Asks the group to scaffold this chapter into every translation of the
     * version, in a session of its own.
     *
     * The reasoning and the failure policy are [ChapterScaffolding]'s; this is
     * the one-chapter call into it. Sharing that code with the upload screen is
     * the point -- there is one answer to what a chapter's scaffolding is, and
     * two copies of a cross product would be two chances to disagree about what
     * a translation covers.
     *
     * Called before navigating, not after: the route this screen sits on is
     * popped on success, which clears this view model and takes
     * `viewModelScope` with it, so work launched afterwards would be cancelled
     * somewhere in the middle.
     */
    private suspend fun scaffoldIntoTranslations(
        localChatRoom: LocalChatRoom,
        artifactVersion: MantraArtifactVersion,
        chapterIndex: Int,
        chapterSessionId: String,
    ) {
        ChapterScaffolding.propose(
            mantraRepository = mantraRepository,
            frostSigningRepository = frostSigningRepository,
            localChatRoom = localChatRoom,
            userPublicKey = activeUserPublicKey,
            artifactVersionId = artifactVersion.id,
            chapters = listOf(
                ChapterScaffolding.ProposedChapter(
                    sessionId = chapterSessionId,
                    index = chapterIndex,
                )
            ),
        )
    }

    companion object {
        private const val TAG = "AddChapterViewModel"

        /**
         * The most paragraphs a chapter can be proposed with.
         *
         * A chapter is signed together with a chunk per paragraph, and the
         * session signs at most [FrostSigningManager.MAX_BATCH_SIZE] events. The
         * chapter is one of them, so the paragraphs get the rest. A longer
         * chapter has to be split into two, which is a real limit and belongs in
         * front of whoever is typing rather than in a failure afterwards.
         */
        const val MAX_CHUNKS_PER_CHAPTER: Int = FrostSigningManager.MAX_BATCH_SIZE - 1

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
