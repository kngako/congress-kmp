package press.mantra.compose.database.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * When a request line in a transcript stops asking for something.
 *
 * The transcript renders a request with a tint and a "Review" affordance, and
 * that is a promise: tapping it leads to a decision still there to be made.
 * Keeping the promise means knowing when the decision has gone, and the rows are
 * all there is to know it from -- a line rendered days later has no session
 * loaded, and the room may have signed several things since.
 *
 * A signing line says which session it belongs to, so those are read exactly. A
 * ceremony line, and any line written before that column existed, is read by the
 * clock, which is right for the one ritual a room runs at a time.
 *
 * Two ways for a request to be over, and they are not the same. Answering it
 * leaves a line of the reader's own and earns the tick. A session ending
 * underneath it leaves nothing of theirs at all: a member who declined published
 * nothing, and a quorum that signed without them wanted nothing. Both must drop
 * the summons; neither may claim the member signed.
 */
class TranscriptRequestStateTest {
    private val user = "u".repeat(64)
    private val other = "o".repeat(64)

    private var lastId = 0L

    /** One transcript row, with only the five fields either rule reads. */
    private fun line(
        type: String,
        at: Long,
        sender: String = user,
        session: String? = null
    ) = ChatMessage(
        id = ++lastId,
        senderPublicKey = sender,
        isUserMessage = sender == user,
        giftWrapPayloadId = null,
        marmotGroupEventId = null,
        marmotInnerEventId = null,
        chatRoomId = "room",
        content = "",
        messageType = type,
        frostSigningSessionId = session,
        createdAt = Instant.fromEpochSeconds(at)
    )

    @Test
    fun `a signing request nobody has acted on is still asking`() {
        val request = line(ChatMessage.TYPE_FROST_APPROVAL_NEEDED, at = 10)
        val transcript = listOf(line(ChatMessage.TYPE_FROST_STARTED, at = 9, sender = other), request)

        assertEquals(emptySet(), ChatMessage.answeredRequests(transcript))
        assertEquals(emptySet(), ChatMessage.settledRequests(transcript))
    }

    @Test
    fun `publishing the nonce answers the request that asked for it`() {
        val request = line(ChatMessage.TYPE_FROST_APPROVAL_NEEDED, at = 10)
        val transcript = listOf(request, line(ChatMessage.TYPE_FROST_NONCE, at = 11))

        assertEquals(setOf(request.id), ChatMessage.answeredRequests(transcript))
    }

    @Test
    fun `somebody else's nonce answers nothing`() {
        // The fulfilment has to be this device's own: a transcript is full of other
        // members taking the step this reader has yet to take.
        val request = line(ChatMessage.TYPE_FROST_APPROVAL_NEEDED, at = 10)
        val transcript = listOf(request, line(ChatMessage.TYPE_FROST_NONCE, at = 11, sender = other))

        assertEquals(emptySet(), ChatMessage.answeredRequests(transcript))
    }

    @Test
    fun `declining settles the request without claiming it was signed`() {
        // Declining publishes nothing, so there is no fulfilment to find. The
        // failure the refusal writes is the only trace, and it has to be enough --
        // otherwise the line goes on offering a decision already made.
        val request = line(ChatMessage.TYPE_FROST_APPROVAL_NEEDED, at = 10)
        val transcript = listOf(request, line(ChatMessage.TYPE_FROST_FAILED, at = 11))

        assertEquals(setOf(request.id), ChatMessage.settledRequests(transcript))
        assertEquals(emptySet(), ChatMessage.answeredRequests(transcript))
    }

    @Test
    fun `a group that signs without this member settles their request`() {
        // A t-of-n key does not need everybody. Nothing of this member's is in the
        // signature and nothing of theirs was ever published, so answered stays
        // empty -- but there is no longer anything for them to decide.
        val request = line(ChatMessage.TYPE_FROST_APPROVAL_NEEDED, at = 10)
        val transcript = listOf(request, line(ChatMessage.TYPE_FROST_COMPLETE, at = 12, sender = other))

        assertEquals(setOf(request.id), ChatMessage.settledRequests(transcript))
        assertEquals(emptySet(), ChatMessage.answeredRequests(transcript))
    }

    @Test
    fun `an earlier session's ending does not close a later request`() {
        // Rooms sign more than once, and the previous session's last line sits
        // above this one's first.
        val request = line(ChatMessage.TYPE_FROST_APPROVAL_NEEDED, at = 20)
        val transcript = listOf(line(ChatMessage.TYPE_FROST_COMPLETE, at = 9, sender = other), request)

        assertEquals(emptySet(), ChatMessage.settledRequests(transcript))
    }

    @Test
    fun `a nonce answers its own session's request and not the other's`() {
        // The case this column exists for. A chapter and the translation that
        // depends on it are proposed as two sessions at once, so the transcript
        // holds two open requests, and answering one leaves the other asking.
        val chapter = line(ChatMessage.TYPE_FROST_APPROVAL_NEEDED, at = 10, session = "chapter")
        val translation = line(ChatMessage.TYPE_FROST_APPROVAL_NEEDED, at = 11, session = "translation")

        val transcript = listOf(
            chapter,
            translation,
            line(ChatMessage.TYPE_FROST_NONCE, at = 12, session = "chapter")
        )

        assertEquals(setOf(chapter.id), ChatMessage.answeredRequests(transcript))
        assertEquals(emptySet(), ChatMessage.settledRequests(transcript))
    }

    @Test
    fun `one session finishing settles nothing in the other`() {
        // Same two proposals, ended the other way: a quorum signs the chapter
        // without this member. Their translation request is untouched by it --
        // the group is still waiting on them for that one.
        val chapter = line(ChatMessage.TYPE_FROST_APPROVAL_NEEDED, at = 10, session = "chapter")
        val translation = line(ChatMessage.TYPE_FROST_APPROVAL_NEEDED, at = 11, session = "translation")

        val transcript = listOf(
            chapter,
            translation,
            line(ChatMessage.TYPE_FROST_COMPLETE, at = 12, sender = other, session = "chapter")
        )

        assertEquals(setOf(chapter.id), ChatMessage.settledRequests(transcript))
        assertEquals(emptySet(), ChatMessage.answeredRequests(transcript))
    }

    @Test
    fun `a line that names no session is still read by the clock`() {
        // Rows written before the column existed carry null, and there is nothing
        // else to read them by. Rooms that old could only sign one thing at a
        // time, so the clock is not a guess there -- it is the whole truth.
        val request = line(ChatMessage.TYPE_FROST_APPROVAL_NEEDED, at = 10, session = "chapter")

        val transcript = listOf(request, line(ChatMessage.TYPE_FROST_NONCE, at = 11))

        assertEquals(setOf(request.id), ChatMessage.answeredRequests(transcript))
    }

    @Test
    fun `a ceremony step is settled by nothing`() {
        // Signing is the one thing a member can refuse, so it is the only place a
        // request can be over without them having answered it. A ceremony step is
        // either taken or still waited on, and reading either ending as the end of
        // one would drop a summons the ritual is still stalled on.
        val request = line(ChatMessage.TYPE_DKG_APPROVAL_NEEDED_ROUND_1, at = 10)
        val transcript = listOf(
            request,
            line(ChatMessage.TYPE_FROST_FAILED, at = 11),
            line(ChatMessage.TYPE_DKG_FAILED, at = 12, sender = other)
        )

        assertEquals(emptySet(), ChatMessage.settledRequests(transcript))
    }

    @Test
    fun `each ceremony step is answered only by its own`() {
        val hostKey = line(ChatMessage.TYPE_DKG_APPROVAL_NEEDED_HOST_KEY, at = 10)
        val roundOne = line(ChatMessage.TYPE_DKG_APPROVAL_NEEDED_ROUND_1, at = 12)
        val transcript = listOf(hostKey, line(ChatMessage.TYPE_DKG_HOST_KEY, at = 11), roundOne)

        assertEquals(setOf(hostKey.id), ChatMessage.answeredRequests(transcript))
    }

    @Test
    fun `twelve open proposals each keep their own summons`() {
        // An upload of twelve files opens twelve sessions at once, interleaved in
        // one stretch of transcript. The clock fallback would have the first
        // answer drop every later request, which is the failure two concurrent
        // sessions already had once -- and twelve is where a path that still
        // falls back to the clock would show.
        val requests = (1..12).map { n ->
            line(ChatMessage.TYPE_FROST_APPROVAL_NEEDED, at = 100L + n, session = "session-" + n)
        }

        // Answered in the opposite order to the asking, so neither the clock nor
        // the list order could produce the right answer by accident.
        val answers = listOf(12, 7, 1).map { n ->
            line(ChatMessage.TYPE_FROST_NONCE, at = 200L + n, session = "session-" + n)
        }

        val answered = ChatMessage.answeredRequests(requests + answers)

        assertEquals(
            setOf(requests[11].id, requests[6].id, requests[0].id),
            answered,
        )
    }

    @Test
    fun `one proposal failing settles only itself`() {
        val requests = (1..12).map { n ->
            line(ChatMessage.TYPE_FROST_APPROVAL_NEEDED, at = 100L + n, session = "session-" + n)
        }
        val failure = line(ChatMessage.TYPE_FROST_FAILED, at = 300, session = "session-5")

        // The other eleven chapters are still in front of the group. A batch is
        // all-or-nothing within a session and nothing across them, which is the
        // whole reason an upload is N sessions rather than one.
        assertEquals(setOf(requests[4].id), ChatMessage.settledRequests(requests + listOf(failure)))
    }
}
