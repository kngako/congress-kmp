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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import press.mantra.compose.database.model.MantraArtifactVersion
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.files.PickedFile
import press.mantra.compose.managers.ChapterIndexing
import press.mantra.compose.managers.ChapterScaffolding
import press.mantra.compose.managers.FrostSigningManager
import press.mantra.compose.nostr.nip30303.ChapterEvent
import press.mantra.compose.nostr.nip30303.ChunkEvent
import press.mantra.compose.repository.ChatRepository
import press.mantra.compose.repository.FrostSigningRepository
import press.mantra.compose.repository.MantraRepository
import press.mantra.compose.text.Markdown
import press.mantra.compose.ui.view.state.PickedChapter
import press.mantra.compose.ui.view.state.ProposeOutcome
import press.mantra.compose.ui.view.state.UploadChaptersUIState
import press.mantra.compose.ui.view.state.UploadReview
import press.mantra.compose.ui.view.state.UploadProblem

/**
 * Several files in, one signing session per chapter out.
 *
 * **Why not one session for the lot.** The web app this mirrors does N files in
 * one transaction; a batch cannot. A session signs at most
 * [FrostSigningManager.MAX_BATCH_SIZE] events and a chapter spends one place
 * with its paragraphs taking the rest, so two chapters of thirty paragraphs
 * already overflow. Worse, a batch is all-or-nothing: one chapter that failed to
 * reach a quorum would take the other eleven with it, and a book is precisely a
 * set of things that do not need to arrive together. And the approval gate's own
 * rule is that it shows every event rather than a count, so twelve chapters in
 * one gate would be a worse prompt than the twelve it replaced.
 *
 * So an upload is N proposals, each needing its own quorum. That is a real
 * imposition on every other member of the room, and the only person in a
 * position to weigh it is the one picking files -- which is why the screen says
 * so before the picker opens, and why [MAX_FILES_PER_UPLOAD] exists.
 *
 * **Partial success is the expected outcome, not an error.** Nothing can retract
 * a proposal, so if the fourth fails the first three are real and the group can
 * still sign them. There is no rollback and no navigating away on failure: the
 * rows that went are marked, the rest keep their reason, and the button becomes
 * a retry.
 */
class UploadChaptersViewModel(
    val artifactId: String,
    val chatRoomId: String,
    val activeUserPublicKey: HexKey,
    val relayHint: String?,
    initialUIState: UploadChaptersUIState,
    val mantraRepository: MantraRepository,
    val chatRepository: ChatRepository,
    val frostSigningRepository: FrostSigningRepository,
) : ViewModel() {

    /**
     * A `StateFlow` rather than `by mutableStateOf`, and not as a matter of
     * taste.
     *
     * View models here are built by `viewModel(factory = ...)` *inside*
     * composition, so a `mutableStateOf` created in a constructor belongs to
     * that composition's snapshot -- and a write from another thread that lands
     * before the composition is applied is silently discarded. No exception, no
     * log; the state keeps its initial value for the life of the process.
     *
     * [initiate] writes from `Dispatchers.IO`, and the reads it does are a
     * primary-key lookup and a `canSign` check: a few milliseconds, which is
     * exactly the width of that window. The failure would be this screen sitting
     * on its loading indicator for ever on a fast device while a slow one worked.
     * That bug has already been had here once; see
     * `SovereignWalletViewModel.listWalletState`.
     */
    private val _uiState = MutableStateFlow(initialUIState)
    val uiState: StateFlow<UploadChaptersUIState> = _uiState.asStateFlow()

    private val logger = Logger.withTag(TAG)

    /**
     * The text of every chapter this version already has, against its name.
     *
     * Held so a picked file can be recognised as one that is already in the
     * version. The whole artifact's prose in memory is not free, and it is the
     * price of answering the question at all: two chapters with the same text
     * are two different event ids, and nothing later can merge them.
     */
    private var existingByText: Map<String, String> = emptyMap()

    /** Counts passes, so two with the same outcome are still two announcements. */
    private var passes: Int = 0

    fun initiate() {
        viewModelScope.launch(Dispatchers.IO) {
            val artifact = mantraRepository.getArtifact(artifactId)
            val localChatRoom = chatRepository.getChatRoomByIdentifier(chatRoomId)

            _uiState.value = when {
                artifact == null -> UploadChaptersUIState.Error("Couldn't find the artifact")
                localChatRoom == null -> UploadChaptersUIState.Error("Couldn't find the chat room")
                else -> {
                    val version = mantraRepository.getArtifactVersions(artifactId).firstOrNull()

                    existingByText = version?.let { v ->
                        mantraRepository.getChaptersForArtifactVersion(v.id)
                            .associate { it.originalText to it.name }
                    }.orEmpty()

                    UploadChaptersUIState.Loaded(
                        localChatRoom = localChatRoom,
                        artifact = artifact,
                        artifactVersion = version,
                        canSign = frostSigningRepository.canSign(chatRoomId),
                        baseIndex = version?.let { nextIndex(it) } ?: 0,
                    )
                }
            }
        }
    }

    /**
     * Decodes picked files into rows, appending to whatever is already listed.
     *
     * Appending rather than replacing, so a second visit to the picker adds to
     * the upload instead of discarding it -- a reader who meant to replace can
     * remove rows, and one who meant to add cannot undo a replacement.
     */
    fun addFiles(files: List<PickedFile>) {
        if (files.isEmpty()) return

        updateLoaded { loaded ->
            val room = loaded.chapters.size
            val added = UploadReview.rows(
                files = files.take((MAX_FILES_PER_UPLOAD - room).coerceAtLeast(0)),
                startOrdinal = room,
            )

            loaded.copy(chapters = withProblems(loaded.chapters + added))
        }
    }

    fun rename(id: String, name: String) {
        updateLoaded { loaded ->
            loaded.copy(
                chapters = withProblems(
                    loaded.chapters.map { if (it.id == id) it.copy(name = name) else it }
                )
            )
        }
    }

    fun remove(id: String) {
        updateLoaded { loaded ->
            loaded.copy(chapters = withProblems(loaded.chapters.filterNot { it.id == id }))
        }
    }

    private fun withProblems(chapters: List<PickedChapter>): List<PickedChapter> =
        UploadReview.withProblems(chapters, existingByText)

    private inline fun updateLoaded(
        crossinline block: (UploadChaptersUIState.Loaded) -> UploadChaptersUIState.Loaded,
    ) {
        _uiState.update { state ->
            if (state is UploadChaptersUIState.Loaded) block(state) else state
        }
    }

    /**
     * Puts every proposable chapter to the group, one session each, in order.
     *
     * **Sequentially, not concurrently.** Each proposal writes a session and
     * publishes group events, so N at once is N interleaved MLS epochs -- and a
     * group event that arrives a moment out of order is dropped for good; see
     * `docs/mls-skipped-keys.md`. An upload is not in a hurry.
     *
     * Indices are allocated once here rather than re-read between sessions. The
     * base is read fresh, because a chapter may have arrived since the files
     * were picked, and then they are handed out in order -- re-asking per
     * chapter would make the numbering depend on how fast the sessions are
     * written.
     */
    fun propose(
        localChatRoom: LocalChatRoom,
        artifactVersion: MantraArtifactVersion,
        onDone: (proposed: Int, failed: Int) -> Unit,
    ) {
        val loaded = _uiState.value as? UploadChaptersUIState.Loaded ?: return

        // A pass already running will call [onDone] itself when it finishes;
        // firing it here as well would announce one pass twice.
        if (loaded.isProposing) return

        val queue = loaded.proposable.map { it.id }
        if (queue.isEmpty()) {
            // Nothing to do still has to be said. The FAB is disabled in this
            // case so the screen never reaches it, but a callback that
            // sometimes never fires is a trap for the next caller -- and it is
            // what a test of a second tap found.
            onDone(0, 0)
            return
        }

        updateLoaded { it.copy(isProposing = true) }

        viewModelScope.launch(Dispatchers.IO) {
            var index = nextIndex(artifactVersion)
            var proposed = 0
            var failed = 0
            val scaffolded = mutableListOf<ChapterScaffolding.ProposedChapter>()

            for (id in queue) {
                val row = (_uiState.value as? UploadChaptersUIState.Loaded)
                    ?.chapters
                    ?.firstOrNull { it.id == id }
                    ?: continue

                val session = runCatching {
                    frostSigningRepository.proposeSigningBatch(
                        localChatRoom = localChatRoom,
                        userPublicKey = activeUserPublicKey,
                        lead = ChapterEvent.build(
                            artifactVersionId = artifactVersion.id,
                            name = row.name,
                            originalText = row.markdown,
                            index = index,
                            wordCount = row.wordCount,
                            characterCount = row.characterCount,
                        ),
                        // The chunks are cut from the chapter the session
                        // authored, because a chunk names its chapter's id and
                        // that id is a hash over the group's key at the room's
                        // path -- which this view model neither knows nor should.
                        dependents = ChunkEvent::splitOf,
                    )
                }.onFailure { error ->
                    logger.e("Could not propose ${row.fileName} as a chapter", error)
                }.getOrNull()

                if (session != null) {
                    markProposed(id, session.id)
                    scaffolded += ChapterScaffolding.ProposedChapter(
                        sessionId = session.id,
                        index = index,
                    )
                    proposed++
                    index++
                } else {
                    markFailed(id)
                    failed++
                }
            }

            // One batch for every chapter that went, not one per chapter. Twelve
            // chapters in a three-dialect group is thirty-six join rows, which
            // fit in a single session; the per-chapter shape would have opened
            // twelve of them to carry three rows each.
            //
            // After the loop rather than inside it, so a chapter that failed to
            // propose contributes no scaffolding and the ones that succeeded
            // still get theirs.
            ChapterScaffolding.propose(
                mantraRepository = mantraRepository,
                frostSigningRepository = frostSigningRepository,
                localChatRoom = localChatRoom,
                userPublicKey = activeUserPublicKey,
                artifactVersionId = artifactVersion.id,
                chapters = scaffolded,
            )

            passes++
            updateLoaded {
                it.copy(
                    isProposing = false,
                    lastOutcome = ProposeOutcome(
                        proposed = proposed,
                        failed = failed,
                        pass = passes,
                    ),
                )
            }

            viewModelScope.launch(Dispatchers.Main) { onDone(proposed, failed) }
        }
    }

    private fun markProposed(id: String, sessionId: String) {
        updateLoaded { loaded ->
            loaded.copy(
                chapters = loaded.chapters.map {
                    if (it.id == id) it.copy(sessionId = sessionId, problem = null) else it
                }
            )
        }
    }

    private fun markFailed(id: String) {
        updateLoaded { loaded ->
            loaded.copy(
                chapters = loaded.chapters.map {
                    if (it.id == id) it.copy(problem = UploadProblem.ProposalFailed) else it
                }
            )
        }
    }

    private suspend fun nextIndex(artifactVersion: MantraArtifactVersion): Int =
        ChapterIndexing.nextIndex(
            mantraRepository = mantraRepository,
            frostSigningRepository = frostSigningRepository,
            chatRoomId = chatRoomId,
            artifactVersionId = artifactVersion.id,
        )

    companion object {
        private const val TAG = "UploadChaptersViewModel"

        /**
         * The most files one upload will take.
         *
         * Not a protocol limit -- the protocol has no opinion -- but a limit on
         * how much of the room's attention one tap may spend. Twenty files is
         * twenty proposals, each of which every other member is asked to read
         * and approve. Somebody who means to upload a hundred chapters can do it
         * in five passes and will know they did.
         */
        const val MAX_FILES_PER_UPLOAD: Int = 20

        fun factory(
            activeUserPublicKey: HexKey,
            artifactId: String,
            chatRoomId: String,
            relayHint: String?,
            initialUIState: UploadChaptersUIState = UploadChaptersUIState.Loading,
            mantraRepository: MantraRepository,
            chatRepository: ChatRepository,
            frostSigningRepository: FrostSigningRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                UploadChaptersViewModel(
                    activeUserPublicKey = activeUserPublicKey,
                    artifactId = artifactId,
                    chatRoomId = chatRoomId,
                    relayHint = relayHint,
                    initialUIState = initialUIState,
                    mantraRepository = mantraRepository,
                    chatRepository = chatRepository,
                    frostSigningRepository = frostSigningRepository,
                )
            }
        }
    }
}
