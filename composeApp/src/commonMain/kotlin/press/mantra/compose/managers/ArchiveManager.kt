package press.mantra.compose.managers

import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quartz.utils.TimeUtils
import press.mantra.compose.database.MantraDatabase
import press.mantra.compose.database.model.ChatMessage
import press.mantra.compose.database.model.GroupSignedEvent
import press.mantra.compose.database.model.MarmotInnerEvent
import press.mantra.compose.extensions.toHex
import kotlin.time.Clock
import kotlin.time.Instant
import press.mantra.compose.nostr.archive.ArchiveEvent
import press.mantra.compose.nostr.archive.ArchiveRequestEvent
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
 * Every payload here is rebuilt from a `Mantra*` row with `toXEvent()`, and
 * stands or falls on that rebuild being byte-identical to what was signed.
 * `ArchiveRoundTripTest` is what says it is, per kind, against a real quorum --
 * and it found two faults in the artifact's rebuild the first time it ran, both
 * of which would have shipped payloads that every receiver drops as forgeries
 * without a word.
 *
 * That was once the only way: a signed event was applied and what survived was
 * the row. `GroupSignedEvent` now keeps the event too, and [applyPage] files one
 * for every payload it accepts -- which is what lets a member who was handed
 * their history hand it on. Assembly still walks the rows, because a room whose
 * work predates that table has no events on file and rebuilding is the only way
 * to reach it. Reading assembled events from the table instead is worth doing
 * once the fallback can be dropped.
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

    // ---- Asking, and answering -------------------------------------------

    /**
     * Ask the group for its signed history, if this device holds none of it.
     *
     * Called on entering a room. The condition is deliberately crude -- no
     * dialects and no artifacts -- because the three cases it has to catch look
     * identical from inside the database and should not be told apart:
     *
     *  - a member added after the work was done, whose invite may be recent;
     *  - a reinstall, whose invite is long past;
     *  - a second device, which was never invited at all.
     *
     * Returns whether a request went out.
     *
     * ### Why a device asks rather than being sent one
     *
     * A push from the inviter is an application message in the epoch the add
     * created, and one that overtakes the Welcome is dropped and not deferred --
     * silently, while the inviter sees a success. See docs/marmot-membership.md.
     * Sending a request cannot lose that race, because being able to send it is
     * the proof the race was won: a device that can put an application message
     * into the room has processed its Welcome.
     */
    suspend fun requestIfEmpty(
        database: MantraDatabase,
        chatRoomId: String,
        userPublicKey: HexKey,
    ): Boolean {
        val chatRoom = database.chatRoomDao().findChatRoomById(chatRoomId)?.chatRoom ?: return false
        if (chatRoom.archiveRequestedAt != null) return false

        val holdsWork = database.mantraDialectDao().getDialectsByChatRoomId(chatRoomId).isNotEmpty() ||
            database.mantraArtifactDao().getArtifactsByChatRoomId(chatRoomId).isNotEmpty()

        if (holdsWork) return false

        queue(
            database = database,
            chatRoomId = chatRoomId,
            userPublicKey = userPublicKey,
            kind = ArchiveRequestEvent.KIND,
            tags = ArchiveRequestEvent.build().tags,
            content = "",
        )

        database.chatRoomDao().upsert(chatRoom.copy(archiveRequestedAt = Clock.System.now()))

        announce(
            database = database,
            chatRoomId = chatRoomId,
            userPublicKey = userPublicKey,
            messageType = ChatMessage.TYPE_ARCHIVE_REQUESTED,
            content = "Asked this group for its signed work",
        )

        logger.i("Asked $chatRoomId for its signed history")

        return true
    }

    /**
     * Send [recipient] everything this device can prove about [chatRoomId].
     *
     * Both ways an archive goes out are this one call: answering a request, and
     * the push behind a Welcome. Named for what it does rather than for either
     * occasion, because the two differ only in who decided.
     *
     * Any member may send one and none is elected to. A duplicate costs bandwidth
     * and nothing else -- pages are idempotent and every member who is not the
     * recipient ignores them -- so it is waste rather than damage, and the
     * stand-down that would avoid it is an optimisation to add on top rather than
     * a correctness gap to close first.
     *
     * A device with nothing signed sends nothing. Silence is the honest reply
     * from a member who is themselves still catching up, and an empty archive
     * would look like an answer.
     */
    suspend fun sendTo(
        database: MantraDatabase,
        chatRoomId: String,
        userPublicKey: HexKey,
        recipient: HexKey,
    ): Int {
        if (recipient.equals(userPublicKey, ignoreCase = true)) return 0

        val pages = assemble(database, chatRoomId, recipient)
        if (pages.isEmpty()) {
            logger.i("Nothing signed here to send ${recipient.take(8)} for $chatRoomId")
            return 0
        }

        pages.forEach { page ->
            queue(
                database = database,
                chatRoomId = chatRoomId,
                userPublicKey = userPublicKey,
                kind = page.kind,
                tags = page.tags,
                content = page.content,
                createdAt = Instant.fromEpochSeconds(page.createdAt),
            )
        }

        val name = database.profileDao().getProfileByPublicKey(recipient)
            ?.humanReadableNameOrPubkey()
            ?: recipient.take(8)

        announce(
            database = database,
            chatRoomId = chatRoomId,
            userPublicKey = userPublicKey,
            messageType = ChatMessage.TYPE_ARCHIVE_SENT,
            content = "Sent this group's signed work to $name",
        )

        logger.i("Sending ${recipient.take(8)} ${pages.size} archive page(s) for $chatRoomId")

        return pages.size
    }

    /**
     * One line in the room's transcript.
     *
     * Written by the device it happened on, for itself. None of these travels --
     * an archive is not an event in the group's life, it is one member being
     * caught up -- so a member watching from the side sees nothing, correctly.
     *
     * Content is a whole sentence rather than a predicate, so these stay out of
     * the AUTHORED sets and nothing prefixes a name to them. See
     * [ChatMessage.ARCHIVE_TYPES].
     */
    private suspend fun announce(
        database: MantraDatabase,
        chatRoomId: String,
        userPublicKey: HexKey,
        messageType: String,
        content: String,
    ) {
        database.chatMessageDao().upsert(
            ChatMessage(
                content = content,
                messageType = messageType,
                chatRoomId = chatRoomId,
                senderPublicKey = userPublicKey,
                isUserMessage = true,
                giftWrapPayloadId = null,
                marmotGroupEventId = null,
                marmotInnerEventId = null,
            )
        )
    }

    /**
     * Put one event on the room's outbound queue.
     *
     * The same shape `FrostSigningManager.broadcast` uses: a `MarmotInnerEvent`
     * with no group event yet, which the notary picks up and encrypts into a
     * kind:445 for the room. The id is the rumor id the outbound pipeline
     * recomputes from these fields when it assembles the event to encrypt.
     *
     * **No `ChatMessage`.** Broadcast does not depend on one -- it is
     * unconditional in `encryptAndSendMarmotInnerEvent`, which is what let the
     * signing sessions travel with no transcript line -- and an archive that
     * filed one per page would put a row of envelopes in the room's history. One
     * line per archive is right, and it is not writable from here, because the
     * pages of an archive are indistinguishable from each other at this point.
     */
    private suspend fun queue(
        database: MantraDatabase,
        chatRoomId: String,
        userPublicKey: HexKey,
        kind: Int,
        tags: TagArray,
        content: String,
        createdAt: Instant = Clock.System.now(),
    ) {
        database.marmotInnerEventDao().upsert(
            MarmotInnerEvent(
                id = EventHasher.hashId(
                    pubKey = userPublicKey,
                    createdAt = createdAt.epochSeconds,
                    tags = tags,
                    content = content,
                    kind = kind
                ),
                publicKey = userPublicKey,
                kind = kind,
                createdAt = createdAt,
                tags = tags,
                content = content,
                chatRoomId = chatRoomId,
            )
        )
    }

    // ---- Receiving ------------------------------------------------------

    /**
     * What one pass over a room's stored pages did.
     *
     * [skipped] and [failed] are kept apart because only one of them is worth
     * trying again. A payload the allowlist or the signature refused will be
     * refused identically forever; a payload that threw was probably missing a
     * row it depends on, and the next page may bring it.
     */
    data class Outcome(
        val applied: Int = 0,
        val skipped: Int = 0,
        val failed: Int = 0,
    ) {
        operator fun plus(other: Outcome) = Outcome(
            applied = applied + other.applied,
            skipped = skipped + other.skipped,
            failed = failed + other.failed,
        )
    }

    /**
     * Take delivery of an archive page for [userPublicKey].
     *
     * The page is already on disk by the time this runs -- the inbound path
     * stores every inner event it decrypts before dispatching on kind -- so this
     * does not apply the arriving page as such. It sweeps every page the room
     * has, which covers the new one and any that arrived before the rows they
     * depend on.
     *
     * **A device that is not the named recipient does nothing.** The page is an
     * ordinary group message and it can read it; it has no reason to. It already
     * holds the work, and re-applying would rewrite every one of its rows to
     * point at an archive page rather than at the event that introduced it. That
     * is also what keeps the sweep bounded: only the member being caught up ever
     * builds the list.
     */
    suspend fun receive(
        database: MantraDatabase,
        chatRoomId: String,
        userPublicKey: HexKey,
        page: Event,
    ): Outcome {
        val recipient = ArchiveEvent(
            id = page.id,
            pubKey = page.pubKey,
            createdAt = page.createdAt,
            tags = page.tags,
            content = page.content,
            sig = page.sig,
        ).recipient()

        if (!recipient.equals(userPublicKey, ignoreCase = true)) {
            logger.d("Archive page ${page.id.take(8)} is for ${recipient?.take(8)}; not applying")
            return Outcome()
        }

        return sweep(database, chatRoomId, userPublicKey)
    }

    /**
     * Apply every archive page this room holds for [userPublicKey], repeatedly,
     * until a pass stops making progress.
     *
     * Pages arrive over relays in no order, so page 3 can land before page 2 and
     * its chunks have no chapter to hang off yet. Those payloads throw a foreign
     * key violation and would be lost -- unless something runs them again.
     *
     * **No table is needed for that**, because the inbound path already stores
     * every inner event it decrypts. This is the same shape
     * `FrostSigningManager.replayStoredMessages` has, and for the same reason:
     * nothing was actually lost, it just had nowhere to go at the time.
     *
     * **Progress is measured by failures falling, not by rows written.** Every
     * write here is an upsert keyed on the event id, so "applied something" is
     * true on every pass forever and would not terminate. A pass that fails fewer
     * payloads than the last one learned something; a pass that does not is as
     * far as this archive gets, and the rest is a hole to be filled by another
     * page or another archive.
     *
     * **The answer is the last pass, not the sum of them.** Accumulating would
     * count a payload once per pass it survived and report failures the next pass
     * went on to fix, so `failed > 0` would stop meaning "still missing". The
     * final pass is the settled state: what these pages can apply, what they will
     * never apply, and what is still waiting on something that has not arrived.
     */
    suspend fun sweep(
        database: MantraDatabase,
        chatRoomId: String,
        userPublicKey: HexKey,
    ): Outcome {
        val pages = database.marmotInnerEventDao()
            .getByChatRoomAndKinds(chatRoomId, listOf(ArchiveEvent.KIND))
            .filter { addressedTo(it, userPublicKey) }

        if (pages.isEmpty()) return Outcome()

        var pass = Outcome()
        var previousFailures = Int.MAX_VALUE

        while (true) {
            pass = Outcome()
            pages.forEach { pass += applyPage(database, chatRoomId, it) }

            if (pass.failed == 0 || pass.failed >= previousFailures) {
                if (pass.failed > 0) {
                    logger.w(
                        "Stopped sweeping $chatRoomId with ${pass.failed} payload(s) still " +
                            "unapplied: nothing in the pages it holds satisfies them"
                    )
                }
                break
            }

            previousFailures = pass.failed
        }

        logger.i(
            "Swept ${pages.size} archive page(s) for $chatRoomId: " +
                "${pass.applied} applied, ${pass.skipped} skipped, ${pass.failed} left"
        )

        // An answer arrived, so the room may ask again if it turns out to be a
        // partial one. Cleared on anything applied rather than on the archive
        // reporting itself complete: a page count is the sender's claim about the
        // transfer, not about the group's record, and a member who left work out
        // would otherwise have the last word.
        if (pass.applied > 0) {
            database.chatRoomDao().findChatRoomById(chatRoomId)?.chatRoom?.let { chatRoom ->
                if (chatRoom.archiveRequestedAt != null) {
                    database.chatRoomDao().upsert(chatRoom.copy(archiveRequestedAt = null))

                    // One line per answered request, which is as close to one per
                    // archive as this can get: the pages of an archive are not
                    // distinguishable from each other here, and clearing the stamp
                    // is exactly the moment a catch-up stops being pending.
                    //
                    // A push behind a Welcome writes none, because the room was
                    // never asked. That is right: it lands before the member has
                    // opened the room, and "caught up on work you have not seen
                    // yet" is a line about nothing.
                    announce(
                        database = database,
                        chatRoomId = chatRoomId,
                        userPublicKey = userPublicKey,
                        messageType = ChatMessage.TYPE_ARCHIVE_RECEIVED,
                        content = "Caught up on ${pass.applied} item(s) of this group's signed work",
                    )
                }
            }
        }

        return pass
    }

    private fun addressedTo(stored: MarmotInnerEvent, userPublicKey: HexKey): Boolean =
        ArchiveEvent(
            id = stored.id,
            pubKey = stored.publicKey,
            createdAt = stored.createdAt.epochSeconds,
            tags = stored.tags,
            content = stored.content,
            sig = "",
        ).recipient().equals(userPublicKey, ignoreCase = true)

    /**
     * One page, checked payload by payload and applied in dependency order.
     *
     * **Per payload, not per page.** A forged payload sitting beside honest ones
     * costs itself and nothing else -- the same rule `MarmotInboundManager` uses
     * for a forged direct message, and for the same reason: this runs inside the
     * inbound transaction, and one bad event must not take the room down with it.
     * Refusing the whole page would also let a single forgery deny an entire
     * archive.
     *
     * The page's own framing is still all-or-nothing; see
     * [ArchiveEvent.decodePage] for why those two are not in tension.
     */
    private suspend fun applyPage(
        database: MantraDatabase,
        chatRoomId: String,
        stored: MarmotInnerEvent,
    ): Outcome {
        val payloads = ArchiveEvent.decodePage(stored.content)
        if (payloads == null) {
            logger.w("Archive page ${stored.id.take(8)} does not read as a page; dropping it")
            return Outcome()
        }

        // Read once for the page rather than per payload: every event in an
        // archive was signed by the room it is arriving in, so they all share
        // its path. Null when the recipient has no key state yet -- which is
        // the normal case for the member an archive exists for, and why
        // `GroupSignedEvent.derivationPath` is nullable and `record` fills it
        // in later rather than overwriting it with null.
        val derivationPath = GroupKeyStateManager.keyStateFor(database, chatRoomId)?.derivationPath

        var outcome = Outcome()

        ArchiveEvent.inApplyOrder(payloads).forEach { payload ->
            // The allowlist first, and it is not a formality. Verification admits
            // an event to the apply path on the strength of the group's
            // signature, which makes every kind the group has ever signed
            // replayable by any member at any time -- a GroupKeyStateEvent from
            // an earlier epoch passes the signature check perfectly.
            if (!ArchiveEvent.isArchivable(payload.kind)) {
                logger.w(
                    "Archive page ${stored.id.take(8)} carries kind ${payload.kind}, " +
                        "which an archive may not deliver; dropping ${payload.id.take(8)}"
                )
                outcome += Outcome(skipped = 1)
                return@forEach
            }

            if (!GroupKeyStateEvent.isSignedByRoom(payload, chatRoomId)) {
                logger.w(
                    "Archive page ${stored.id.take(8)} carries ${payload.id.take(8)}, " +
                        "which room $chatRoomId did not sign; dropping it"
                )
                outcome += Outcome(skipped = 1)
                return@forEach
            }

            outcome += try {
                // Kept as the group signed it, before it is turned into rows.
                // The check above is what earns it that: a payload reaching here
                // is one the room signed, which is the same standard
                // `FrostSigningManager` records its own batches on. Without this
                // a member who was handed their history would hold the rows and
                // none of the events, and could never build an archive of their
                // own to hand on.
                database.groupSignedEventDao().record(
                    GroupSignedEvent.fromEvent(
                        event = payload,
                        chatRoomId = chatRoomId,
                        derivationPath = derivationPath,
                    )
                )

                // The chat line this returns is deliberately dropped rather than
                // filed. ChatMessage has an autoGenerate primary key, so there is
                // no id to dedupe on and every applied payload would mint a new
                // row -- giving the recipient a synthetic transcript dated now,
                // and another one on every pass of the sweep. The archive
                // restores the work; the conversation is forward secret and stays
                // gone.
                ChatMessage.applyInnerEvent(
                    database = database,
                    groupId = chatRoomId,
                    event = payload,
                    marmotGroupEventId = stored.marmotGroupEventId,
                    marmotInnerEventId = stored.id,
                    senderPublicKey = payload.pubKey,
                    isUserMessage = false,
                    createdAt = stored.createdAt,
                    groupSignedEventId = payload.id,
                )
                Outcome(applied = 1)
            } catch (error: Throwable) {
                // Almost always a foreign key: this payload names a row that is
                // in a page which has not arrived yet. Retryable, which is what
                // the sweep is for, so it is counted rather than logged loudly.
                logger.d(
                    "Could not apply ${payload.id.take(8)} from archive page " +
                        "${stored.id.take(8)} yet: ${error.message}"
                )
                Outcome(failed = 1)
            }
        }

        return outcome
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
            // artifact was signed with, and `ArtifactVersionEvent.initialVersionOf`
            // gives it the artifact's own timestamp. The label is still not on the
            // artifact row -- `fromArtifactEvent` drops it -- so this is where it
            // comes back from. An artifact with no such version is one this device
            // cannot rebuild, which is a gap in the archive rather than a reason to
            // abandon it.
            val versionLabel = versions
                .firstOrNull { it.createdAt == artifact.createdAt }
                ?.versionLabel

            if (versionLabel == null) {
                logger.w("Artifact ${artifact.id} has no initial version; leaving it out")
            } else {
                add(artifact.toArtifactEvent(versionLabel = versionLabel))
            }

            versions.forEach { version ->
                add(version.toArtifactVersionEvent())

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
                            .forEach { translationChapter ->
                                add(translationChapter.toTranslationChapterEvent())

                                // Only the translation that survived supersession
                                // is here to be found: retranslating a passage
                                // changes the text and so the event id, and the
                                // arm that applies one drops what it replaces. So
                                // an archive carries a group's current answer to
                                // each passage rather than its drafts, which is
                                // the same thing every other member holds.
                                database.mantraTranslationChunkDao()
                                    .getTranslationChunksByTranslationChapterId(translationChapter.id)
                                    .forEach { add(it.toTranslationChunkEvent()) }
                            }
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
