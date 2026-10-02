package press.mantra.compose.managers

import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.repository.FrostSigningRepository
import press.mantra.compose.repository.MantraRepository

/**
 * Asking the group to scaffold newly proposed chapters into every translation
 * of their version.
 *
 * A translation chapter carries no words -- it is `(translation, chapter,
 * position)` -- and it exists so a translated chunk has somewhere to hang. See
 * [TranslationScaffold] for the rows themselves; this is the part that reads
 * the version, finds the chapters as the group will author them, and opens the
 * sessions.
 *
 * **Its own session, not more items on the chapter's.** A chapter is capped at
 * [TranslationScaffold] -- really at `FrostSigningManager.MAX_BATCH_SIZE` minus
 * the chapter itself -- and every dialect the group works in would take one of
 * those places away. How long a chapter may be and how many languages it is
 * read in have nothing to do with each other, and a shared cap would make the
 * first move whenever somebody did the second.
 *
 * **But one session for all of them, not one per chapter.** This is what the
 * upload needed and what `AddChapterViewModel` had wrong for a single chapter
 * only by not noticing: twelve chapters in a group with three dialects is
 * thirty-six join rows, which fit in one batch, and the per-chapter shape would
 * have opened twelve sessions to carry three rows each. Twenty-four proposals
 * where thirteen will do.
 *
 * **Failure is logged and swallowed**, as it was before. The chapters have
 * already been proposed and are the thing that was asked for; losing them
 * because their scaffolding could not be opened would be the wrong way round. A
 * join row naming a chapter that never reached a quorum fails a foreign key on
 * the way in, is logged by `applySignedEvent`, and never becomes a row -- so the
 * catch-up on the translation is what fills the gap afterwards.
 */
object ChapterScaffolding {

    private const val TAG = "ChapterScaffolding"

    /**
     * A chapter that has just been put to the group, named by the session that
     * will sign it.
     *
     * The session rather than the chapter, because there is no chapter yet. Its
     * id is settled at proposal time -- it is the hash the signers are putting
     * their shares behind -- and [FrostSigningRepository.unsignedEvents] is how
     * to read it while the session is still running.
     */
    data class ProposedChapter(
        val sessionId: String,
        val index: Int,
    )

    /**
     * Opens as few sessions as will carry the join rows for [chapters].
     *
     * Chunked over the **cross product**, not over the translations. With one
     * chapter the two are the same and the old code was right by coincidence;
     * with twelve, chunking the translations would propose twelve chapters'
     * worth of rows in one batch per sixty-four dialects, which is the wrong
     * axis and overflows at the fifth chapter of a three-dialect group.
     *
     * Returns the sessions opened, which is nothing the caller has to act on --
     * it is there so a test can count them.
     */
    suspend fun propose(
        mantraRepository: MantraRepository,
        frostSigningRepository: FrostSigningRepository,
        localChatRoom: LocalChatRoom,
        userPublicKey: HexKey,
        artifactVersionId: String,
        chapters: List<ProposedChapter>,
    ): List<String> {
        if (chapters.isEmpty()) return emptyList()

        return runCatching {
            val translations = mantraRepository
                .getTranslationsForArtifactVersion(artifactVersionId)
                .ifEmpty { return emptyList() }

            // The chapters as the group will author them. A session whose lead
            // event cannot be read is left out rather than guessed at: a join
            // row naming the wrong chapter is worse than one that is missing,
            // because the catch-up can find a missing one.
            val sources = chapters.mapNotNull { proposed ->
                val lead = frostSigningRepository
                    .unsignedEvents(frostSigningRepository.getItems(proposed.sessionId))
                    .firstOrNull()
                    ?: return@mapNotNull null

                TranslationScaffold.SourceChapter(id = lead.id, index = proposed.index) to
                    lead.createdAt
            }
            if (sources.isEmpty()) return emptyList()

            // One timestamp for the lot, so a scaffolding proposed as one act
            // reads as one. The latest of the chapters' own, which is the moment
            // the act finished -- not the clock, which would be a third time
            // unrelated to either end of it.
            val createdAt = sources.maxOf { it.second }

            val rows = TranslationScaffold.chaptersOf(
                translationArtifactVersionIds = translations.map { it.id },
                chapters = sources.map { it.first },
                createdAt = createdAt,
            )

            rows.chunked(FrostSigningManager.MAX_BATCH_SIZE).mapNotNull { batch ->
                frostSigningRepository.proposeSigningBatch(
                    localChatRoom = localChatRoom,
                    userPublicKey = userPublicKey,
                    events = batch,
                )?.id
            }
        }.onFailure { error ->
            Logger.withTag(TAG).e(
                "Proposed ${chapters.size} chapter(s) but could not ask for them in this " +
                    "version's translations",
                error,
            )
        }.getOrDefault(emptyList())
    }
}
