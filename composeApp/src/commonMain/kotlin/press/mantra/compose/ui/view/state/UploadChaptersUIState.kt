package press.mantra.compose.ui.view.state

import androidx.compose.runtime.Immutable
import press.mantra.compose.database.model.MantraArtifact
import press.mantra.compose.database.model.MantraArtifactVersion
import press.mantra.compose.database.model.intermdiate.LocalChatRoom

sealed interface UploadChaptersUIState {
    data object Loading : UploadChaptersUIState

    data class Error(val message: String) : UploadChaptersUIState

    data class Loaded(
        val localChatRoom: LocalChatRoom,
        val artifact: MantraArtifact,
        /** Chapters hang off a version; without one there is nothing to upload into. */
        val artifactVersion: MantraArtifactVersion?,
        val canSign: Boolean,
        /**
         * The files picked so far, in the order they will be proposed.
         *
         * Nothing here has been proposed until a row carries a session id. The
         * screen is a review step, which is the one place this departs visibly
         * from the web app it mirrors: there, twelve files become twelve
         * chapters that belong to the uploader and can be fixed afterwards. Here
         * they become twelve requests for the group's own signature, and
         * anything wrong in one is wrong in something a quorum is about to put
         * its name to.
         */
        val chapters: List<PickedChapter> = emptyList(),
        /**
         * The index the first proposable chapter will take.
         *
         * Read when the files are picked so the list can show where each one
         * lands, and read again when proposing -- a chapter may have arrived in
         * between, and the signed number has to be the later answer.
         */
        val baseIndex: Int = 0,
        val isProposing: Boolean = false,
        /**
         * What the last pass did, so the screen can say so.
         *
         * In the state rather than handed to the callback, because the sentence
         * is a formatted string resource and `stringResource` is composable
         * while a callback is not. Read in composition, shown from an effect.
         */
        val lastOutcome: ProposeOutcome? = null,
    ) : UploadChaptersUIState {

        /** Rows that could be proposed as they stand. */
        val proposable: List<PickedChapter>
            get() = chapters.filter { it.sessionId == null && it.problem?.blocking != true }

        val proposed: List<PickedChapter> get() = chapters.filter { it.sessionId != null }

        val canPropose: Boolean
            get() = artifactVersion != null && canSign && !isProposing && proposable.isNotEmpty()
    }
}

/** The result of one pass of proposing. */
@Immutable
data class ProposeOutcome(
    val proposed: Int,
    val failed: Int,
    /** Distinguishes two passes with identical counts, so the second is still announced. */
    val pass: Int,
)

/**
 * One picked file, as the chapter it would become.
 *
 * [markdown] is already decoded, BOM-stripped and newline-normalised by
 * `ChapterText.decode`, so it is the text that would be signed rather than
 * whatever the file held.
 */
@Immutable
data class PickedChapter(
    /** Stable across edits and removals, so the list keeps its identity. */
    val id: String,
    val fileName: String,
    /**
     * Editable, defaulting to `ChapterNaming.forFile`.
     *
     * A filename is right often enough to be the default and wrong often enough
     * -- `chapter-01.md`, `ch1_final_v2.md` -- that it has to be fixable before a
     * quorum sees it. The name is signed into the chapter along with the text.
     */
    val name: String,
    val markdown: String,
    val chunks: List<String>,
    val wordCount: Int,
    val characterCount: Int,
    val proposalBytes: Int,
    val problem: UploadProblem? = null,
    /** Set once this chapter has been put to the group. Never unset. */
    val sessionId: String? = null,
)

/**
 * What is wrong with one row.
 *
 * Per row rather than per upload, because a bad file should not cost the other
 * eleven. [blocking] is the difference between "this cannot be proposed" and
 * "this is probably not what you meant".
 */
@Immutable
sealed interface UploadProblem {
    val blocking: Boolean

    /** Not decodable as UTF-8, or carrying a NUL. */
    data object NotText : UploadProblem {
        override val blocking: Boolean get() = true
    }

    /** Over `ChapterText.MAX_FILE_BYTES`. */
    data object TooLarge : UploadProblem {
        override val blocking: Boolean get() = true
    }

    /** Nothing left after trimming, which is a chapter with nothing to translate. */
    data object Empty : UploadProblem {
        override val blocking: Boolean get() = true
    }

    /** More paragraphs than one session signs. */
    data class TooManyChunks(val count: Int) : UploadProblem {
        override val blocking: Boolean get() = true
    }

    /** Within the chunk cap but over what a proposal carries. */
    data class TooLargeToPropose(val bytes: Int) : UploadProblem {
        override val blocking: Boolean get() = true
    }

    /** No name, and nothing in the file to derive one from. */
    data object Unnamed : UploadProblem {
        override val blocking: Boolean get() = true
    }

    /**
     * The same text as a chapter this version already has, or as another row.
     *
     * **A warning, not a block.** A book can repeat a page. But the common cause
     * is picking the same directory twice, and two chapters with the same text
     * are two different event ids that no later de-duplication can merge -- so
     * it is worth saying before a quorum signs both.
     */
    data class Duplicate(val of: String) : UploadProblem {
        override val blocking: Boolean get() = false
    }

    /**
     * Proposing this one failed.
     *
     * **Not blocking**, because a retry is exactly what should happen: a failed
     * proposal is retried by making a new one, never by re-opening the session
     * that failed, and `proposeSigningBatch` mints fresh nonce seeds by
     * construction. The row keeps its reason so "propose the remaining" has
     * something to point at.
     */
    data object ProposalFailed : UploadProblem {
        override val blocking: Boolean get() = false
    }
}
