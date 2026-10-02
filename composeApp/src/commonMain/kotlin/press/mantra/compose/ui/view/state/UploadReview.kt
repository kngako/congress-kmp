package press.mantra.compose.ui.view.state

import press.mantra.compose.files.PickedFile
import press.mantra.compose.managers.FrostSigningManager
import press.mantra.compose.nostr.nip30303.ChapterProposal
import press.mantra.compose.text.ChapterNaming
import press.mantra.compose.text.ChapterText
import press.mantra.compose.text.Markdown

/**
 * Turning picked files into reviewable rows, and deciding what is wrong with
 * each.
 *
 * Pure, and separate from `UploadChaptersViewModel` on purpose. Everything here
 * is a function of the files, the names typed over them and the chapters the
 * version already has -- no repository, no coroutine, no database -- which is
 * most of what the upload screen actually does and all of what is worth testing
 * closely. What the view model keeps is the orchestration: reading the version,
 * allocating indices and opening a session per chapter.
 */
object UploadReview {

    /** The most paragraphs a chapter can be proposed with; the chapter takes a place too. */
    const val MAX_CHUNKS_PER_CHAPTER: Int = FrostSigningManager.MAX_BATCH_SIZE - 1

    /**
     * Rows for [files], numbered from [startOrdinal] so they can be appended to
     * rows already listed.
     *
     * Problems are not decided here -- see [withProblems] -- beyond the two that
     * are properties of the bytes rather than of the list.
     */
    fun rows(files: List<PickedFile>, startOrdinal: Int): List<PickedChapter> =
        files.mapIndexed { offset, file -> row(file, startOrdinal + offset) }

    private fun row(file: PickedFile, ordinal: Int): PickedChapter {
        val decoded = ChapterText.decode(file.bytes)
        val markdown = (decoded as? ChapterText.Result.Text)?.markdown.orEmpty()
        val chunks = Markdown.splitChunks(markdown)

        return PickedChapter(
            // Neither the file's name nor its text is unique -- two directories
            // can each hold a `01.md`, and a book can repeat a page -- and the
            // ordinal is, within the one upload this id has to be unique in.
            id = "$ordinal:${file.name}",
            fileName = file.name,
            name = if (decoded is ChapterText.Result.Text) {
                ChapterNaming.forFile(file.name, markdown)
            } else {
                ""
            },
            markdown = markdown,
            chunks = chunks,
            wordCount = Markdown.wordCount(markdown),
            characterCount = Markdown.characterCount(markdown),
            proposalBytes = ChapterProposal.proposalBytes(markdown, chunks),
            problem = when (decoded) {
                ChapterText.Result.NotText -> UploadProblem.NotText
                ChapterText.Result.TooLarge -> UploadProblem.TooLarge
                is ChapterText.Result.Text -> null
            },
        )
    }

    /**
     * Re-decides every row's problem.
     *
     * Whole-list rather than per row, because two of the answers are about the
     * list: a duplicate is a relation between rows, and removing one row can
     * clear the warning on another.
     *
     * A row already proposed is returned untouched. Its chapter is in front of
     * the group, and saying something new about it would be a comment on a
     * decision already taken.
     *
     * [existingByText] maps the text of each chapter the version already has to
     * its name, so a file that is already in the version can be recognised as
     * one -- by its text, since that is what two chapters would have in common.
     */
    fun withProblems(
        chapters: List<PickedChapter>,
        existingByText: Map<String, String>,
    ): List<PickedChapter> {
        val seen = mutableMapOf<String, String>()

        return chapters.map { chapter ->
            if (chapter.sessionId != null) return@map chapter

            // Nothing about the list can fix a file that is not text, so those
            // two survive whatever else is decided below.
            if (chapter.problem == UploadProblem.NotText ||
                chapter.problem == UploadProblem.TooLarge
            ) {
                return@map chapter
            }

            val duplicateOf = existingByText[chapter.markdown] ?: seen[chapter.markdown]
            if (!seen.containsKey(chapter.markdown)) {
                seen[chapter.markdown] = chapter.name.ifBlank { chapter.fileName }
            }

            chapter.copy(
                problem = when {
                    chapter.chunks.isEmpty() -> UploadProblem.Empty
                    chapter.name.isBlank() -> UploadProblem.Unnamed
                    chapter.chunks.size > MAX_CHUNKS_PER_CHAPTER ->
                        UploadProblem.TooManyChunks(chapter.chunks.size)
                    !ChapterProposal.fits(chapter.markdown, chapter.chunks) ->
                        UploadProblem.TooLargeToPropose(chapter.proposalBytes)
                    duplicateOf != null -> UploadProblem.Duplicate(duplicateOf)
                    // A row that failed to propose keeps saying so until it is
                    // retried, which is what makes the button honest about there
                    // being something left to do.
                    chapter.problem == UploadProblem.ProposalFailed ->
                        UploadProblem.ProposalFailed
                    else -> null
                }
            )
        }
    }
}
