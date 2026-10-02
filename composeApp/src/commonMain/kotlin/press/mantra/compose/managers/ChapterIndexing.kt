package press.mantra.compose.managers

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.first
import press.mantra.compose.nostr.nip30303.ChapterEvent
import press.mantra.compose.repository.FrostSigningRepository
import press.mantra.compose.repository.MantraRepository

/**
 * Where the next chapter of a version goes.
 *
 * The index is signed into the chapter and cannot be recounted on arrival -- a
 * device applying one cannot recount from a version other members may have
 * added to in a different order -- so the number has to be right at proposal
 * time. "How many chapters are in the table" is not it.
 *
 * **A proposed chapter is not a row.** It becomes one when a quorum signs,
 * which takes as long as it takes, and until then nothing in
 * `MantraChapter` knows about it. So reading the table gives the same answer to
 * two proposals made a second apart, and both get signed at the same index.
 * At one chapter at a time that is a race nobody hits; an upload of twelve
 * files hits it eleven times out of eleven.
 *
 * The information needed is already published. A session's items carry the
 * unsigned events it is signing, and an unsigned event's id and fields are
 * settled at proposal time -- that is the whole reason
 * [FrostSigningRepository.unsignedEvents] exists rather than only
 * `signedEvents`. So the answer is the table *and* the room's open proposals.
 */
object ChapterIndexing {

    private const val TAG = "ChapterIndexing"

    /**
     * The index for the next chapter of [artifactVersionId], counting chapters
     * this room has proposed but not yet signed.
     *
     * **One past the highest, not a count.** A count is wrong the moment the
     * sequence has a gap, and gaps are expected here: a chapter that fails to
     * reach a quorum never becomes a row, and its index is spent -- a
     * re-proposed chapter is a different event. Ordering survives a gap, so
     * leaving one is the cheap answer and renumbering would be the expensive
     * wrong one.
     *
     * **Every session counts, whatever stage it is at.** A failed session's
     * index is wasted, which costs a gap; a stalled session's chapter may still
     * arrive, and stepping over it costs nothing. Filtering by stage would
     * trade a harmless gap for a possible collision.
     *
     * **This fixes one device, not the group.** Two members proposing at the
     * same moment still collide: neither sees the other's proposal until it
     * arrives, and no amount of local reading will change that. That is
     * tolerable where this is not -- an index orders reading, two chapters at
     * index 4 sort arbitrarily between themselves, and nothing else breaks. The
     * local case is worth fixing because an upload makes it certain rather than
     * unlikely.
     *
     * Failing soft on purpose: if the proposals cannot be read, the table's
     * answer is still better than nothing, and refusing to propose a chapter
     * because its number could not be checked would be the wrong trade.
     */
    suspend fun nextIndex(
        mantraRepository: MantraRepository,
        frostSigningRepository: FrostSigningRepository,
        chatRoomId: String,
        artifactVersionId: String,
    ): Int {
        val signed = mantraRepository
            .getChaptersForArtifactVersion(artifactVersionId)
            .maxOfOrNull { it.index }

        val proposed = runCatching {
            proposedIndices(frostSigningRepository, chatRoomId, artifactVersionId).maxOrNull()
        }.onFailure { error ->
            Logger.withTag(TAG).e(
                "Could not read this room's proposals; numbering the next chapter from the " +
                    "table alone, which may collide with a chapter already proposed",
                error,
            )
        }.getOrNull()

        val highest = listOfNotNull(signed, proposed).maxOrNull() ?: return 0
        return highest + 1
    }

    /**
     * The indices of chapters this room has proposed for [artifactVersionId].
     *
     * Read off the unsigned events rather than from anything stored about them,
     * because there is nothing stored about them: a proposal's items are its
     * only record until it is signed. Non-chapter items -- a dialect, a
     * translation, the chunks beside a chapter -- are ignored by kind, and a
     * chapter belonging to another version of the same artifact by its
     * `artifactVersionId` tag.
     */
    private suspend fun proposedIndices(
        frostSigningRepository: FrostSigningRepository,
        chatRoomId: String,
        artifactVersionId: String,
    ): List<Int> =
        frostSigningRepository.observeSessionsForChatRoom(chatRoomId).first()
            .flatMap { session -> frostSigningRepository.unsignedEvents(session.items) }
            .filter { event -> event.kind == ChapterEvent.KIND }
            .mapNotNull { event ->
                ChapterEvent(
                    id = event.id,
                    pubKey = event.pubKey,
                    createdAt = event.createdAt,
                    tags = event.tags,
                    content = event.content,
                    sig = event.sig,
                ).takeIf { it.artifactVersionId() == artifactVersionId }?.index()
            }
}
