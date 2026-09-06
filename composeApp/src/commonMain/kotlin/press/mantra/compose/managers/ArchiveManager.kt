package press.mantra.compose.managers

import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quartz.utils.TimeUtils
import press.mantra.compose.database.MantraDatabase
import press.mantra.compose.extensions.toHex
import press.mantra.compose.nostr.archive.ArchiveEvent
import press.mantra.compose.nostr.frost.GroupKeyStateEvent

/**
 * Building the group's signed record into pages a member who lacks it can apply.
 *
 * See docs/member-archive.md. The short of it: a member added after the work was
 * done has none of it and never will, because a group-signed event is applied
 * locally by each device that took part and never goes on the wire. This is how
 * it gets to them.
 *
 * ### The rows are the archive
 *
 * Signed events are not stored as events. `FrostSigningManager.complete` applies
 * one and what survives is a `Mantra*` row, so every payload here is rebuilt with
 * `toXEvent()` and stands or falls on that rebuild being byte-identical to what
 * was signed. `ArchiveRoundTripTest` is what says it is, per kind, against a real
 * quorum -- and it found two faults in the artifact's rebuild the first time it
 * ran, both of which would have shipped payloads that every receiver drops as
 * forgeries without a word.
 *
 * ### Nothing unverifiable leaves
 *
 * Every rebuilt event is checked with [GroupKeyStateEvent.isSignedByRoom] before
 * it is packed, against the same room id the recipient will check it with. That
 * is not politeness towards the receiver, who checks anyway. It is what keeps an
 * archive honest about its own size: a row that came from a member's rumor
 * cannot be archived, and dropping it here rather than letting the recipient
 * drop it means the page count says what will actually arrive.
 */
object ArchiveManager {
    private const val TAG = "ArchiveManager"

    private val logger = Logger.withTag(TAG)

    /**
     * Every group-signed event this device holds for [chatRoomId], paged and
     * addressed to [recipient].
     *
     * Empty when there is nothing to send -- a room with no shared key, a room
     * whose work is all member rumors, or a device that is itself behind. An
     * empty result is not an error and the caller should not report one: the
     * honest answer to "send them the history" in a room with no signed history
     * is nothing.
     */
    suspend fun assemble(
        database: MantraDatabase,
        chatRoomId: String,
        recipient: HexKey,
        archiveId: String = RandomInstance.bytes(32).toHex(),
        createdAt: Long = TimeUtils.now(),
    ): List<EventTemplate<ArchiveEvent>> {
        val rebuilt = signedEventsOf(database, chatRoomId)

        val verified = rebuilt.filter { GroupKeyStateEvent.isSignedByRoom(it, chatRoomId) }
        if (verified.size != rebuilt.size) {
            // Expected rather than alarming: an artifact version is derived
            // rather than signed, and a translation is its author's rumor. What
            // would be worth looking at is this dropping something the group
            // really did sign, which reads as a broken `toXEvent` rather than as
            // a missing signature.
            logger.d(
                "Leaving ${rebuilt.size - verified.size} of ${rebuilt.size} row(s) out of " +
                    "$chatRoomId's archive: nothing verifiably signed by the room"
            )
        }

        val pages = paginate(ArchiveEvent.inApplyOrder(verified))
        if (pages.isEmpty()) {
            logger.i("Nothing signed to archive for room $chatRoomId")
            return emptyList()
        }

        logger.i(
            "Archiving ${verified.size} event(s) for $chatRoomId as $archiveId, " +
                "${pages.size} page(s) for ${recipient.take(8)}"
        )

        return pages.mapIndexed { index, payloads ->
            ArchiveEvent.build(
                payloads = payloads,
                archiveId = archiveId,
                index = index,
                count = pages.size,
                recipient = recipient,
                createdAt = createdAt,
            )
        }
    }

    /**
     * The room's rows, rebuilt into the events they came from.
     *
     * Walked down the tree rather than queried per kind, because only dialects
     * and artifacts have a by-room query and the rest hang off a parent. The walk
     * is also what makes an artifact's version label reachable: it is not on the
     * artifact row -- see `MantraArtifact.toArtifactEvent` -- and the version it
     * went into is one step away here.
     *
     * Order does not matter at this point; [ArchiveEvent.inApplyOrder] settles it
     * afterwards. What matters is that nothing is missed, so this returns
     * everything and the verify filter above decides what can travel.
     */
    private suspend fun signedEventsOf(
        database: MantraDatabase,
        chatRoomId: String,
    ): List<Event> = buildList {
        database.mantraDialectDao().getDialectsByChatRoomId(chatRoomId).forEach {
            add(it.toDialectEvent())
        }

        database.mantraArtifactDao().getArtifactsByChatRoomId(chatRoomId).forEach { artifact ->
            val versions = database.mantraArtifactVersionDao()
                .getArtifactVersionsByArtifactId(artifact.id)

            // The version an artifact starts life with carries the label the
            // artifact was signed with, and `initialVersionOf` gives it the
            // artifact's own timestamp. A row with none of those is an artifact
            // this device cannot rebuild, which is a gap in the archive rather
            // than a reason to abandon it.
            val versionLabel = versions
                .firstOrNull { it.createdAt == artifact.createdAt }
                ?.versionLabel

            if (versionLabel == null) {
                logger.w("Artifact ${artifact.id} has no initial version; leaving it out")
            } else {
                add(artifact.toArtifactEvent(versionLabel = versionLabel))
            }

            versions.forEach { version ->
                database.mantraChapterDao()
                    .getChaptersByArtifactVersionId(version.id)
                    .forEach { chapter ->
                        add(chapter.toChapterEvent())

                        database.mantraChunkDao()
                            .getChunksByChapterId(chapter.id)
                            .forEach { add(it.toChunkEvent()) }
                    }

                database.mantraTranslationArtifactVersionDao()
                    .getTranslationsByArtifactVersionId(version.id)
                    .forEach { translationVersion ->
                        add(translationVersion.toTranslationArtifactVersionEvent())

                        database.mantraTranslationChapterDao()
                            .getTranslationChaptersByTranslationArtifactVersionId(translationVersion.id)
                            .forEach { add(it.toTranslationChapterEvent()) }
                    }
            }
        }
    }

    /**
     * [events] cut into pages that fit, keeping the order they arrive in.
     *
     * Greedy: fill a page until the next event would cross either cap. Both are
     * checked because they bind different archives -- a room of one-line dialects
     * hits the count first and a room of chapters hits the bytes.
     *
     * An event too large to share a page with anything is given one of its own.
     * One too large for even that is dropped with a log rather than failing the
     * archive: a chapter nobody can archive is a hole, and a member who gets
     * nothing at all is a bigger one.
     */
    private fun paginate(events: List<Event>): List<List<Event>> {
        val pages = mutableListOf<List<Event>>()
        var page = mutableListOf<Event>()
        // The brackets an empty page already costs.
        var bytes = 2

        events.forEach { event ->
            // Plus the comma this event needs if it is not first on its page.
            val size = event.toJson().encodeToByteArray().size + 1

            if (2 + size > ArchiveEvent.MAX_PAGE_BYTES) {
                logger.w(
                    "Event ${event.id} is $size bytes and will not fit a " +
                        "${ArchiveEvent.MAX_PAGE_BYTES}-byte page; leaving it out of the archive"
                )
                return@forEach
            }

            val full = page.isNotEmpty() &&
                (bytes + size > ArchiveEvent.MAX_PAGE_BYTES || page.size >= ArchiveEvent.MAX_PAGE_EVENTS)

            if (full) {
                pages.add(page)
                page = mutableListOf()
                bytes = 2
            }

            page.add(event)
            bytes += size
        }

        if (page.isNotEmpty()) pages.add(page)

        return pages
    }
}
