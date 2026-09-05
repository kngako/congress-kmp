package press.mantra.compose.database.model

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.PrimaryKey
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.name
import press.mantra.compose.database.MantraDatabase
import press.mantra.compose.database.model.traits.LocalStoreEntity
import press.mantra.compose.database.model.traits.SoftDeletableEntity
import press.mantra.compose.database.model.traits.TimestampedEntity
import press.mantra.compose.database.model.traits.UserViewableEntity
import press.mantra.compose.exceptions.MarmotUnprocessableInnerEventException
import press.mantra.compose.extensions.toHex
import com.vitorpamplona.quartz.marmot.GroupEventResult
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import press.mantra.compose.nostr.nip30303.ArtifactEvent
import press.mantra.compose.nostr.nip30303.ArtifactVersionEvent
import press.mantra.compose.nostr.nip30303.ChapterEvent
import press.mantra.compose.nostr.nip30303.ChunkEvent
import press.mantra.compose.nostr.nip30303.DialectEvent
import press.mantra.compose.nostr.nip30303.SubmissionEvent
import press.mantra.compose.nostr.nip30303.TranslationArtifactVersionContributorListEvent
import press.mantra.compose.nostr.nip30303.TranslationArtifactVersionEvent
import press.mantra.compose.nostr.nip30303.TranslationChapterEvent
import press.mantra.compose.nostr.nip30303.TranslationChunkEvent
import press.mantra.compose.nostr.nip30303.TranslationContributorListEvent
import press.mantra.compose.nostr.nip30303.TranslationEvent
import kotlin.time.Clock
import kotlin.time.Instant

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = ChatRoom::class,
            parentColumns = ["id"],
            childColumns = ["chatRoomId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = GiftWrapPayload::class,
            parentColumns = ["id"],
            childColumns = ["giftWrapPayloadId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MarmotGroupEvent::class,
            parentColumns = ["id"],
            childColumns = ["marmotGroupEventId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MarmotInnerEvent::class,
            parentColumns = ["id"],
            childColumns = ["marmotInnerEventId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ChatMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val senderPublicKey: String,
    val isUserMessage: Boolean,

    val giftWrapPayloadId: HexKey?,
    val marmotGroupEventId: HexKey?,
    val marmotInnerEventId: HexKey?,
    val chatRoomId: String,

    val replyToMessageId: Long? = null,
    val quotedMessageId: Long? = null,

    val content: String,
    val messageType: String = "message",

    override val createdAt: Instant = Clock.System.now(),
    override val updatedAt: Instant = createdAt,
    override val savedAt: Instant = Clock.System.now(),
    override val viewedAt: Instant? = null,
    override val deletedAt: Instant? = null, // Who this message should be seen by (used to derive conversationId)
): TimestampedEntity, LocalStoreEntity, UserViewableEntity, SoftDeletableEntity {

    companion object {
        /**
         * A ChillDKG ritual message, as a line in the group's chat.
         *
         * There is one per protocol message the ritual sends -- every member's host
         * key, both of their rounds, and both of the coordinator's aggregates --
         * plus the three that bracket them: started, complete, abandoned.
         *
         * These rows are not anybody's words: no [ChatMessage.giftWrapPayloadId], no
         * event behind them, and nothing sent to say them. Each device writes its own
         * from the ritual messages it already received, which is why they cost no
         * traffic and cannot disagree with the ritual they describe.
         *
         * [ChatMessageListViewModel] renders these as a system line rather than a
         * bubble, since attributing "a shared key ceremony started" to the
         * coordinator would read as something they said.
         */
        const val TYPE_DKG_STARTED = "dkgStarted"
        const val TYPE_DKG_HOST_KEY = "dkgHostKey"
        const val TYPE_DKG_ROUND_1 = "dkgRound1"
        const val TYPE_DKG_COORDINATOR_ROUND_1 = "dkgCoordinatorRound1"
        const val TYPE_DKG_ROUND_2 = "dkgRound2"
        const val TYPE_DKG_CERTIFICATE = "dkgCertificate"
        const val TYPE_DKG_COMPLETE = "dkgComplete"
        const val TYPE_DKG_FAILED = "dkgFailed"

        /**
         * The ritual is waiting on this device's owner and will publish nothing
         * until they act. Unauthored on purpose: these are addressed to the reader
         * rather than said by anyone, so they read "Your approval is needed", not
         * "You your approval is needed".
         *
         * One type per step rather than one for all three, because the row is the
         * only thing the transcript keeps: a line rendered days later has no session
         * to ask what was being requested, so which step it was has to be in the
         * type. Sharing one type left every request wearing the same icon.
         */
        const val TYPE_DKG_APPROVAL_NEEDED_HOST_KEY = "dkgApprovalNeededHostKey"
        const val TYPE_DKG_APPROVAL_NEEDED_ROUND_1 = "dkgApprovalNeededRound1"
        const val TYPE_DKG_APPROVAL_NEEDED_ROUND_2 = "dkgApprovalNeededRound2"

        /**
         * The single type the three above replaced. Still recognised so rows already
         * written keep rendering as system lines instead of turning into chat
         * bubbles attributed to the reader. Nothing writes it any more.
         */
        const val TYPE_DKG_APPROVAL_NEEDED_LEGACY = "dkgApprovalNeeded"

        /**
         * The line each request is answered by: the step it asked for, published by
         * this device. Approving is the only thing that causes that to be written,
         * so its presence is what marks a request done.
         *
         * The legacy type is absent on purpose -- it does not say which step it was
         * asking for, so nothing can be said about whether it was answered.
         */
        val DKG_REQUEST_FULFILMENTS = mapOf(
            TYPE_DKG_APPROVAL_NEEDED_HOST_KEY to TYPE_DKG_HOST_KEY,
            TYPE_DKG_APPROVAL_NEEDED_ROUND_1 to TYPE_DKG_ROUND_1,
            TYPE_DKG_APPROVAL_NEEDED_ROUND_2 to TYPE_DKG_ROUND_2,
        )

        /** The lines that are asking rather than reporting. */
        val DKG_REQUEST_TYPES = setOf(
            TYPE_DKG_APPROVAL_NEEDED_HOST_KEY,
            TYPE_DKG_APPROVAL_NEEDED_ROUND_1,
            TYPE_DKG_APPROVAL_NEEDED_ROUND_2,
            TYPE_DKG_APPROVAL_NEEDED_LEGACY,
        )

        val DKG_TYPES = setOf(
            TYPE_DKG_STARTED,
            TYPE_DKG_HOST_KEY,
            TYPE_DKG_ROUND_1,
            TYPE_DKG_COORDINATOR_ROUND_1,
            TYPE_DKG_ROUND_2,
            TYPE_DKG_CERTIFICATE,
            TYPE_DKG_COMPLETE,
            TYPE_DKG_FAILED,
        ) + DKG_REQUEST_TYPES

        /**
         * The ritual lines somebody did, as opposed to ones that simply happened.
         * For these [senderPublicKey] is the member who acted, and their content is
         * written as a predicate for their name to be read in front of.
         *
         * Every protocol message is one of these: each is a thing a named member's
         * device sent, and which member it was is the whole point of showing it --
         * a ritual finishes only once every member has taken part, so a stalled one
         * is a question about who has not.
         *
         * A finished ceremony is the exception, and has no actor: the group ends up
         * with a key, nobody hands it to them.
         */
        val DKG_AUTHORED_TYPES = setOf(
            TYPE_DKG_STARTED,
            TYPE_DKG_HOST_KEY,
            TYPE_DKG_ROUND_1,
            TYPE_DKG_COORDINATOR_ROUND_1,
            TYPE_DKG_ROUND_2,
            TYPE_DKG_CERTIFICATE,
            TYPE_DKG_FAILED,
        )

        suspend fun fromGroupEventResult(
            database: MantraDatabase,
            activeKeyPair: KeyPair,
            groupEvent: GroupEvent,
            groupEventResult: GroupEventResult
        ): ChatMessage? {
            return when(groupEventResult) {
                is GroupEventResult.ApplicationMessage -> {
                    val event = Event.fromJsonOrNull(groupEventResult.innerEventJson)
                        ?: throw MarmotUnprocessableInnerEventException("Can't process ${groupEventResult.innerEventJson}")

                    // A submission is an envelope: the nip30303 event it delivers
                    // is in its content, written by whoever wrote it. Everything
                    // else on the wire is the nip30303 event itself. Either way the
                    // row on disk is the outer event -- that is what the group sent
                    // and what the chat line is attributed to.
                    val submission = if (event.kind == SubmissionEvent.KIND) {
                        SubmissionEvent(
                            id = event.id,
                            pubKey = event.pubKey,
                            createdAt = event.createdAt,
                            tags = event.tags,
                            content = event.content,
                            sig = event.sig,
                        )
                    } else {
                        null
                    }
                    val payload = submission?.payload()

                    val innerEvent = MarmotInnerEvent(
                        id = event.id,
                        publicKey = event.pubKey,
                        marmotGroupEventId = groupEvent.id,
                        tags = event.tags,
                        content = event.content,
                        chatRoomId = groupEventResult.groupId,
                        kind = event.kind,
                        payloadEventId = payload?.id,
                        createdAt = Instant.fromEpochSeconds(event.createdAt)
                    )
                    database.marmotInnerEventDao().upsert(innerEvent)

                    // A submission whose payload will not parse, or which carries
                    // another submission, is kept but not applied: there is nothing
                    // here we can turn into a row.
                    if (submission != null && (payload == null || payload.kind == SubmissionEvent.KIND)) {
                        ChatMessage(
                            giftWrapPayloadId = null,
                            messageType = "unsupported",
                            marmotGroupEventId = groupEvent.id,
                            marmotInnerEventId = event.id,
                            senderPublicKey = event.pubKey,
                            isUserMessage = activeKeyPair.pubKey.toHex() == groupEvent.pubKey,
                            chatRoomId = groupEventResult.groupId,
                            createdAt = Instant.fromEpochSeconds(event.createdAt),
                            content = event.content,
                        )
                    } else {
                        applyOrHold(
                            database = database,
                            activeKeyPair = activeKeyPair,
                            groupId = groupEventResult.groupId,
                            innerEvent = innerEvent,
                            event = payload ?: event,
                            isUserMessage = activeKeyPair.pubKey.toHex() == groupEvent.pubKey,
                        )
                    }
                }
                is GroupEventResult.CommitPending -> {
                    ChatMessage(
                        giftWrapPayloadId = null,
                        marmotGroupEventId = groupEvent.id,
                        marmotInnerEventId = null,
                        messageType = "pendingCommit",
                        senderPublicKey = groupEvent.pubKey,
                        isUserMessage = activeKeyPair.pubKey.toHex() == groupEvent.pubKey,
                        chatRoomId = groupEventResult.groupId,
                        createdAt = Instant.fromEpochSeconds(groupEvent.createdAt),
                        content = "Pending Commit in epoch ${groupEventResult.epoch}", // TODO: Figure out what to do here...
                    )
                }
                is GroupEventResult.CommitProcessed -> {
                    ChatMessage(
                        giftWrapPayloadId = null,
                        marmotGroupEventId = groupEvent.id,
                        marmotInnerEventId = null,
                        messageType = "processedCommit",
                        senderPublicKey = groupEvent.pubKey,
                        isUserMessage = activeKeyPair.pubKey.toHex() == groupEvent.pubKey,
                        chatRoomId = groupEventResult.groupId,
                        createdAt = Instant.fromEpochSeconds(groupEvent.createdAt),
                        content = "Processed Commit in epoch ${groupEventResult.newEpoch}", // TODO: Figure out what to do here...
                    )
                }
                is GroupEventResult.Duplicate -> {

//                    ChatMessage(
//                        giftWrapPayloadId = null,
//                        groupEventId = groupEvent.id,
//                        messageType = "duplicate",
//                        senderPublicKey = groupEvent.pubKey,
//                        isUserMessage = activeKeyPair.pubKey.toHex() == groupEvent.pubKey,
//                        chatRoomId = groupEventResult.groupId,
//                        createdAt = Instant.fromEpochSeconds(groupEvent.createdAt),
//                        content = groupEventResult.innerEventJson, // TODO: Figure out what to do here...
//                    )
                    null
                }
                is GroupEventResult.Error -> {
//                    ChatMessage(
//                        giftWrapPayloadId = null,
//                        groupEventId = groupEvent.id,
//                        messageType = "error",
//                        senderPublicKey = groupEvent.pubKey,
//                        isUserMessage = activeKeyPair.pubKey.toHex() == groupEvent.pubKey,
//                        chatRoomId = groupEventResult.groupId,
//                        createdAt = Instant.fromEpochSeconds(groupEvent.createdAt),
//                        content = groupEventResult.innerEventJson, // TODO: Figure out what to do here...
//                    )
                    null
                }
                is GroupEventResult.ProposalStaged -> {
                    ChatMessage(
                        giftWrapPayloadId = null,
                        marmotGroupEventId = groupEvent.id,
                        marmotInnerEventId = null,
                        messageType = "proposalStaged",
                        senderPublicKey = groupEvent.pubKey,
                        isUserMessage = activeKeyPair.pubKey.toHex() == groupEvent.pubKey,
                        chatRoomId = groupEventResult.groupId,
                        createdAt = Instant.fromEpochSeconds(groupEvent.createdAt),
                        content = "Proposal staged: ${groupEventResult.senderLeafIndex}", // TODO: Figure out what to do here...
                    )
                }
                is GroupEventResult.UndecryptableOuterLayer -> {
                    ChatMessage(
                        giftWrapPayloadId = null,
                        marmotGroupEventId = groupEvent.id,
                        marmotInnerEventId = null,
                        messageType = "undecryptableOuterLayer",
                        senderPublicKey = groupEvent.pubKey,
                        isUserMessage = activeKeyPair.pubKey.toHex() == groupEvent.pubKey,
                        chatRoomId = groupEventResult.groupId,
                        createdAt = Instant.fromEpochSeconds(groupEvent.createdAt),
                        content = "Undecryptable Message", // TODO: Figure out what to do here...
                    )
                }
            }
        }

        /**
         * Apply one nip30303 [event] the group delivered, and describe it for
         * the transcript.
         *
         * [event] is what is being applied; the other parameters are how it
         * arrived. They come apart for submissions: the payload is written by
         * whoever wrote it -- possibly nobody in this group -- while
         * [marmotInnerEventId], [senderPublicKey] and [createdAt] all belong to
         * the envelope a member actually sent. Entity rows take their author
         * from [event], so the chat line says who added it and the row says who
         * wrote it.
         */
        /**
         * Apply [event] now, or hold it until what it references turns up.
         *
         * Foreign keys mean a payload cannot become a row before its parent
         * does, and inserting one anyway does not fail politely: SQLite aborts
         * the statement, which rolls back the whole transaction the inbound
         * pipeline runs in -- losing the nostr event, the group event, this
         * submission and the transcript line with it, none of which is retried.
         *
         * So the parent is checked first. A payload that arrives early is kept
         * against the id it waits for and applied later, which is what makes
         * order stop mattering: an admin submitting a backlog can send it in
         * whatever order they hold it, and a member who joined last week can be
         * sent what the group was told last month.
         *
         * A held payload writes no chat line. Nobody said anything yet -- the
         * line appears when it is applied, in the transcript position its own
         * timestamp gives it.
         */
        private suspend fun applyOrHold(
            database: MantraDatabase,
            activeKeyPair: KeyPair,
            groupId: String,
            innerEvent: MarmotInnerEvent,
            event: Event,
            isUserMessage: Boolean,
        ): ChatMessage? {
            missingParentOf(database, event)?.let { missingParent ->
                database.marmotInnerEventDao().upsert(
                    innerEvent.copy(awaitingEventId = missingParent)
                )
                return null
            }

            val chatMessage = applyInnerEvent(
                database = database,
                groupId = groupId,
                event = event,
                marmotGroupEventId = innerEvent.marmotGroupEventId,
                marmotInnerEventId = innerEvent.id,
                senderPublicKey = innerEvent.publicKey,
                isUserMessage = isUserMessage,
                createdAt = innerEvent.createdAt,
            )

            // This may be the parent something else was held for.
            releaseAwaiting(database, activeKeyPair, groupId, event.id)

            return chatMessage
        }

        /**
         * Apply whatever was waiting on [arrivedEventId], now that it is here.
         *
         * Releasing one can release another -- a version unblocks its chapters,
         * a chapter unblocks its chunks -- so this keeps going until nothing
         * more comes unstuck. A payload with a second parent still missing is
         * re-pointed at that one rather than applied, so it waits for the right
         * thing instead of being retried on every arrival.
         */
        private suspend fun releaseAwaiting(
            database: MantraDatabase,
            activeKeyPair: KeyPair,
            groupId: String,
            arrivedEventId: HexKey,
        ) {
            val arrived = ArrayDeque(listOf(arrivedEventId))

            while (arrived.isNotEmpty()) {
                val parentId = arrived.removeFirst()

                database.marmotInnerEventDao().getSubmissionsAwaiting(parentId).forEach { held ->
                    // A submission's content is its payload verbatim; anything
                    // else held is a bare nip30303 event, where the row is the
                    // event and its own columns rebuild it. Reading both out of
                    // the content would strand every bare one here forever.
                    val payload = if (held.kind == SubmissionEvent.KIND) {
                        Event.fromJsonOrNull(held.content)
                    } else {
                        Event(
                            id = held.id,
                            pubKey = held.publicKey,
                            createdAt = held.createdAt.epochSeconds,
                            kind = held.kind,
                            tags = held.tags,
                            content = held.content,
                            sig = "",
                        )
                    } ?: return@forEach

                    missingParentOf(database, payload)?.let { stillMissing ->
                        database.marmotInnerEventDao().upsert(
                            held.copy(awaitingEventId = stillMissing)
                        )
                        return@forEach
                    }

                    database.marmotInnerEventDao().upsert(held.copy(awaitingEventId = null))

                    applyInnerEvent(
                        database = database,
                        groupId = groupId,
                        event = payload,
                        marmotGroupEventId = held.marmotGroupEventId,
                        marmotInnerEventId = held.id,
                        senderPublicKey = held.publicKey,
                        // Held payloads are never ours: we hold the parents of
                        // anything we wrote, having written those too.
                        isUserMessage = false,
                        createdAt = held.createdAt,
                    )?.let { database.chatMessageDao().upsert(it) }

                    arrived.addLast(payload.id)
                }
            }
        }

        /**
         * The event [event] references but which is not on file yet, or null
         * when everything it needs is already here.
         */
        private suspend fun missingParentOf(
            database: MantraDatabase,
            event: Event,
        ): HexKey? = parentRefsOf(event).firstOrNull { !it.exists(database) }?.id

        /**
         * A row some event references, named by id and by the kind of event
         * that would have created it.
         */
        internal data class ParentRef(
            val id: HexKey,
            val kind: Int,
        ) {
            suspend fun exists(database: MantraDatabase): Boolean = when (kind) {
                DialectEvent.KIND ->
                    database.mantraDialectDao().getDialectById(id) != null

                ArtifactEvent.KIND ->
                    database.mantraArtifactDao().getArtifactById(id) != null

                ArtifactVersionEvent.KIND ->
                    database.mantraArtifactVersionDao().getArtifactVersionById(id) != null

                ChapterEvent.KIND ->
                    database.mantraChapterDao().getChapterById(id) != null

                ChunkEvent.KIND ->
                    database.mantraChunkDao().getChunkById(id) != null

                TranslationArtifactVersionEvent.KIND ->
                    database.mantraTranslationArtifactVersionDao().getTranslationById(id) != null

                TranslationChapterEvent.KIND ->
                    database.mantraTranslationChapterDao().getTranslationChapterById(id) != null

                TranslationChunkEvent.KIND ->
                    database.mantraTranslationChunkDao().getTranslationChunkById(id) != null

                // Not a parent anything waits on.
                else -> true
            }
        }

        /**
         * Every row [event] references and the database will insist exists.
         *
         * This is the foreign keys on the Mantra* entities, read off the event
         * instead of the schema. The two have to agree: a parent listed here
         * that the schema does not enforce just delays a payload for no reason,
         * and one the schema enforces but is missing here is a payload that
         * takes the whole inbound transaction down with it.
         *
         * The chat room is deliberately not among them. It is a foreign key,
         * but a payload for a room we are not in never reaches this far.
         */
        internal fun parentRefsOf(event: Event): List<ParentRef> = when (event.kind) {
            ArtifactEvent.KIND ->
                event.asArtifactEvent().let {
                    listOfNotNull(it.dialectId()?.let { id -> ParentRef(id, DialectEvent.KIND) })
                }

            ArtifactVersionEvent.KIND ->
                event.asArtifactVersionEvent().let {
                    listOfNotNull(it.artifactId()?.let { id -> ParentRef(id, ArtifactEvent.KIND) })
                }

            ChapterEvent.KIND ->
                event.asChapterEvent().let {
                    listOfNotNull(
                        it.artifactVersionId()?.let { id -> ParentRef(id, ArtifactVersionEvent.KIND) }
                    )
                }

            ChunkEvent.KIND ->
                event.asChunkEvent().let {
                    listOfNotNull(it.chapterId()?.let { id -> ParentRef(id, ChapterEvent.KIND) })
                }

            TranslationArtifactVersionEvent.KIND ->
                event.asTranslationArtifactVersionEvent().let {
                    listOfNotNull(
                        it.artifactVersionId()?.let { id -> ParentRef(id, ArtifactVersionEvent.KIND) },
                        it.dialectId()?.let { id -> ParentRef(id, DialectEvent.KIND) },
                    )
                }

            TranslationChapterEvent.KIND ->
                event.asTranslationChapterEvent().let {
                    listOfNotNull(
                        it.translationArtifactVersionId()?.let { id ->
                            ParentRef(id, TranslationArtifactVersionEvent.KIND)
                        },
                        it.chapterId()?.let { id -> ParentRef(id, ChapterEvent.KIND) },
                    )
                }

            TranslationChunkEvent.KIND ->
                event.asTranslationChunkEvent().let {
                    listOfNotNull(
                        it.translationChapterId()?.let { id ->
                            ParentRef(id, TranslationChapterEvent.KIND)
                        },
                        it.chunkId()?.let { id -> ParentRef(id, ChunkEvent.KIND) },
                    )
                }

            TranslationEvent.KIND ->
                event.asTranslationEvent().let {
                    listOfNotNull(
                        it.translationChunkId()?.let { id ->
                            ParentRef(id, TranslationChunkEvent.KIND)
                        },
                        it.translationArtifactVersionId()?.let { id ->
                            ParentRef(id, TranslationArtifactVersionEvent.KIND)
                        },
                    )
                }

            // Kinds with no parent to wait for.
            else -> emptyList()
        }

        // A submission's payload arrives as a base Event; these read it back as
        // the kind it says it is, so its tag accessors can be used.
        private fun Event.asArtifactEvent() =
            ArtifactEvent(id, pubKey, createdAt, tags, content, sig)

        private fun Event.asArtifactVersionEvent() =
            ArtifactVersionEvent(id, pubKey, createdAt, tags, content, sig)

        private fun Event.asChapterEvent() =
            ChapterEvent(id, pubKey, createdAt, tags, content, sig)

        private fun Event.asChunkEvent() =
            ChunkEvent(id, pubKey, createdAt, tags, content, sig)

        private fun Event.asTranslationArtifactVersionEvent() =
            TranslationArtifactVersionEvent(id, pubKey, createdAt, tags, content, sig)

        private fun Event.asTranslationChapterEvent() =
            TranslationChapterEvent(id, pubKey, createdAt, tags, content, sig)

        private fun Event.asTranslationChunkEvent() =
            TranslationChunkEvent(id, pubKey, createdAt, tags, content, sig)

        private fun Event.asTranslationEvent() =
            TranslationEvent(id, pubKey, createdAt, tags, content, sig)

        private suspend fun applyInnerEvent(
            database: MantraDatabase,
            groupId: String,
            event: Event,
            marmotGroupEventId: HexKey?,
            marmotInnerEventId: HexKey,
            senderPublicKey: HexKey,
            isUserMessage: Boolean,
            createdAt: Instant,
        ): ChatMessage? {
            return when (event.kind) {
                ChatEvent.KIND -> {
                    ChatMessage(
                        giftWrapPayloadId = null,
                        messageType = "message",
                        marmotGroupEventId = marmotGroupEventId,
                        marmotInnerEventId = marmotInnerEventId,
                        senderPublicKey = senderPublicKey,
                        isUserMessage = isUserMessage,
                        chatRoomId = groupId,
                        createdAt = createdAt,
                        content = event.content, // TODO: Figure out what to do here...
                    )
                }
                ArtifactEvent.KIND -> {
                    MantraArtifact.fromArtifactEvent(
                        artifactEvent = ArtifactEvent(
                            id = event.id,
                            pubKey = event.pubKey,
                            createdAt = event.createdAt,
                            tags = event.tags,
                            content = event.content,
                            sig = event.sig,
                        ),
                        chatRoomId = groupId,
                    )?.let { mantraArtifact ->
                        database.mantraArtifactDao().upsert(
                            mantraArtifact.copy(
                                marmotGroupEventId = marmotGroupEventId,
                            )
                        )

                        ChatMessage(
                            giftWrapPayloadId = null,
                            messageType = "artifact",
                            marmotGroupEventId = marmotGroupEventId,
                            marmotInnerEventId = marmotInnerEventId,
                            senderPublicKey = senderPublicKey,
                            isUserMessage = isUserMessage,
                            chatRoomId = groupId,
                            createdAt = createdAt,
                            content = "Added ${mantraArtifact.name} to artifacts"
                        )
                    }
                }
                ArtifactVersionEvent.KIND -> {
                    MantraArtifactVersion.fromArtifactVersionEvent(
                        artifactVersionEvent = ArtifactVersionEvent(
                            id = event.id,
                            pubKey = event.pubKey,
                            createdAt = event.createdAt,
                            tags = event.tags,
                            content = event.content,
                            sig = event.sig
                        ),
                        chatRoomId = groupId,
                    )?.let { mantraArtifactVersion ->
                        database.mantraArtifactVersionDao().upsert(
                            mantraArtifactVersion.copy(
                                marmotGroupEventId = marmotGroupEventId,
                            )
                        )

                        ChatMessage(
                            giftWrapPayloadId = null,
                            messageType = "artifactVersion",
                            marmotGroupEventId = marmotGroupEventId,
                            marmotInnerEventId = marmotInnerEventId,
                            senderPublicKey = senderPublicKey,
                            isUserMessage = isUserMessage,
                            chatRoomId = groupId,
                            createdAt = createdAt,
                            content = "Added ${mantraArtifactVersion.versionLabel} to artifact versions" // TODO: Use artifact name...
                        )
                    }
                }
                ChapterEvent.KIND -> {
                    MantraChapter.fromChapterEvent(
                        chapterEvent = ChapterEvent(
                            id = event.id,
                            pubKey = event.pubKey,
                            createdAt = event.createdAt,
                            tags = event.tags,
                            content = event.content,
                            sig = event.sig
                        ),
                        chatRoomId = groupId,
                    )?.let { mantraChapter ->
                        database.mantraChapterDao().upsert(
                            mantraChapter.copy(
                                marmotGroupEventId = marmotGroupEventId,
                            )
                        )

                        ChatMessage(
                            giftWrapPayloadId = null,
                            messageType = "chapter",
                            marmotGroupEventId = marmotGroupEventId,
                            marmotInnerEventId = marmotInnerEventId,
                            senderPublicKey = senderPublicKey,
                            isUserMessage = isUserMessage,
                            chatRoomId = groupId,
                            createdAt = createdAt,
                            content = "Added ${mantraChapter.name} to chapters" // TODO: Use artifact name...
                        )
                    }
                }

                ChunkEvent.KIND -> {
                    MantraChunk.fromChunkEvent(
                        chunkEvent = ChunkEvent(
                            id = event.id,
                            pubKey = event.pubKey,
                            createdAt = event.createdAt,
                            tags = event.tags,
                            content = event.content,
                            sig = event.sig
                        ),
                        chatRoomId = groupId,
                    )?.let { mantraChunk ->
                        database.mantraChunkDao().upsert(
                            mantraChunk.copy(
                                marmotGroupEventId = marmotGroupEventId,
                            )
                        )

                        // TODO: Chunks might be too noisy to show in chat...
                    }
                    null
                }
                DialectEvent.KIND -> {
                    MantraDialect.fromDialectEvent(
                        dialectEvent = DialectEvent(
                            id = event.id,
                            pubKey = event.pubKey,
                            createdAt = event.createdAt,
                            tags = event.tags,
                            content = event.content,
                            sig = event.sig
                        ),
                        chatRoomId = groupId,
                    )?.let { mantraDialect ->
                        database.mantraDialectDao().upsert(
                            mantraDialect.copy(
                                marmotGroupEventId = marmotGroupEventId,
                            )
                        )


                        ChatMessage(
                            giftWrapPayloadId = null,
                            messageType = "dialect",
                            marmotGroupEventId = marmotGroupEventId,
                            marmotInnerEventId = marmotInnerEventId,
                            senderPublicKey = senderPublicKey,
                            isUserMessage = isUserMessage,
                            chatRoomId = groupId,
                            createdAt = createdAt,
                            content = "Added ${mantraDialect.name} to dialects" // TODO: Use artifact name...
                        )
                    }
                }
                TranslationArtifactVersionEvent.KIND -> {
                    MantraTranslationArtifactVersion.fromTranslationArtifactVersionEvent(
                        translationArtifactVersionEvent = TranslationArtifactVersionEvent(
                            id = event.id,
                            pubKey = event.pubKey,
                            createdAt = event.createdAt,
                            tags = event.tags,
                            content = event.content,
                            sig = event.sig
                        ),
                        chatRoomId = groupId,
                    )?.let { mantraTranslationArtifactVersion ->
                        database.mantraTranslationArtifactVersionDao().upsert(
                            mantraTranslationArtifactVersion.copy(
                                marmotGroupEventId = marmotGroupEventId,
                            )
                        )

                        ChatMessage(
                            giftWrapPayloadId = null,
                            messageType = "translationArtifactVersion",
                            marmotGroupEventId = marmotGroupEventId,
                            marmotInnerEventId = marmotInnerEventId,
                            senderPublicKey = senderPublicKey,
                            isUserMessage = isUserMessage,
                            chatRoomId = groupId,
                            createdAt = createdAt,
                            content = "Added ${mantraTranslationArtifactVersion.name} to translation artifact versions" // TODO: Use artifact name...
                        )
                    }
                }
                TranslationArtifactVersionContributorListEvent.KIND -> {
                    // TODO: Consume contributors...
                    null
                }
                TranslationChapterEvent.KIND -> {
                    MantraTranslationChapter.fromTranslationChapterEvent(
                        translationChapterEvent = TranslationChapterEvent(
                            id = event.id,
                            pubKey = event.pubKey,
                            createdAt = event.createdAt,
                            tags = event.tags,
                            content = event.content,
                            sig = event.sig
                        ),
                        chatRoomId = groupId,
                    )?.let { mantraTranslationChapter ->
                        database.mantraTranslationChapterDao().upsert(
                            mantraTranslationChapter.copy(
                                marmotGroupEventId = marmotGroupEventId,
                            )
                        )
                        // TODO: translation chapter might be too noisy for chat updates
                    }
                    null
                }
                TranslationChunkEvent.KIND -> {
                    MantraTranslationChunk.fromTranslationChunkEvent(
                        translationChunkEvent = TranslationChunkEvent(
                            id = event.id,
                            pubKey = event.pubKey,
                            createdAt = event.createdAt,
                            tags = event.tags,
                            content = event.content,
                            sig = event.sig
                        ),
                        chatRoomId = groupId,
                    )?.let { mantraTranslationChunk ->
                        database.mantraTranslationChunkDao().upsert(
                            mantraTranslationChunk.copy(
                                marmotGroupEventId = marmotGroupEventId,
                            )
                        )


                    }
                    null
                }
                TranslationContributorListEvent.KIND -> {
                    // TODO: Consume contributors...
                    null
                }
                TranslationEvent.KIND -> {
                    MantraTranslation.fromTranslationEvent(
                        translationEvent = TranslationEvent(
                            id = event.id,
                            pubKey = event.pubKey,
                            createdAt = event.createdAt,
                            tags = event.tags,
                            content = event.content,
                            sig = event.sig
                        ),
                        chatRoomId = groupId,
                    )?.let { mantraTranslation ->
                        database.mantraTranslationDao().upsert(
                            mantraTranslation.copy(
                                marmotGroupEventId = marmotGroupEventId,
                            )
                        )
                        ChatMessage(
                            giftWrapPayloadId = null,
                            messageType = "translation",
                            marmotGroupEventId = marmotGroupEventId,
                            marmotInnerEventId = marmotInnerEventId,
                            senderPublicKey = senderPublicKey,
                            isUserMessage = isUserMessage,
                            chatRoomId = groupId,
                            createdAt = createdAt,
                            content = "Added ${mantraTranslation.text} to translations" // TODO: Reference original text
                        )
                    }
                }
                else -> {
                    ChatMessage(
                        giftWrapPayloadId = null,
                        messageType = "unsupported",
                        marmotGroupEventId = marmotGroupEventId,
                        marmotInnerEventId = marmotInnerEventId,
                        senderPublicKey = senderPublicKey,
                        isUserMessage = isUserMessage,
                        chatRoomId = groupId,
                        createdAt = createdAt,
                        content = event.toJson(),
                    )
                }
            }
        }
    }
}