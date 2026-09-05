package press.mantra.compose.managers

import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Kind
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import com.vitorpamplona.quartz.nip01Core.crypto.Nip01Crypto
import com.vitorpamplona.quartz.utils.RandomInstance
import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.PrivateKey
import fr.acinq.bitcoin.PublicKey
import fr.acinq.bitcoin.crypto.frost.AggregatedNonce
import fr.acinq.bitcoin.crypto.frost.IndividualNonce
import fr.acinq.bitcoin.crypto.frost.SecretNonce
import fr.acinq.bitcoin.crypto.frost.Session
import fr.acinq.bitcoin.crypto.frost.TweakCache
import fr.acinq.bitcoin.utils.Either
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import press.mantra.compose.database.MantraDatabase
import press.mantra.compose.database.model.ChatMessage
import press.mantra.compose.database.model.DkgSession
import press.mantra.compose.database.model.FrostSignerMessage
import press.mantra.compose.database.model.FrostSigningSession
import press.mantra.compose.database.model.GroupKeyState
import press.mantra.compose.database.model.MarmotInnerEvent
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.database.model.types.DkgRitualStage
import press.mantra.compose.database.model.types.FrostSigningStage
import press.mantra.compose.extensions.toHex
import press.mantra.compose.nostr.dkg.DkgRitualEvents
import press.mantra.compose.nostr.frost.FrostSigningEvents

/**
 * Signs a nostr event with a group's FROST threshold key, in its #admins room.
 *
 * The shape is [ChillDkgRitualManager]'s, deliberately: the member who proposes
 * a signature coordinates it, protocol messages travel on the kinds in
 * [FrostSigningEvents], each inbound message is persisted and then the session
 * is asked whether it can move, and every step is recomputed from stored inputs
 * so a device killed mid-round resumes on the next message. What that manager's
 * own notes say about being message-driven applies here unchanged.
 *
 * The transport is not the same one. A ceremony runs over NIP-17 because it has
 * to: its participants are not yet a Marmot group, and its whole purpose is to
 * produce the key one would be keyed on. Signing has the opposite problem
 * solved for it -- the #admins room already exists, its membership is exactly
 * the share holders, and its id is derived from the key -- so a signing message
 * is an ordinary Marmot inner event and needs no addressing of its own. One
 * encrypted group event reaches everyone, rather than one sealed wrap per
 * member per message.
 *
 * Three things are genuinely different, and each of them is why this is a
 * separate manager rather than another branch of that one.
 *
 * ### It does not need everybody
 *
 * A DKG cannot finish until every member takes part; that is what makes the key.
 * Signing with a t-of-n key needs t, and waiting for n would throw away the
 * property the group ran a ceremony to get. So the coordinator waits for the
 * threshold to be reachable, picks a signer set, and says who is in it. Members
 * outside the set do nothing and are not stalling anything.
 *
 * ### Restart-safety is forced, not chosen
 *
 * `SecretNonce` cannot be serialised and refuses to be used twice. Storing the
 * randomness it is derived from and regenerating on demand is the only way a
 * signing session can survive the app closing -- and it is safe only because a
 * session signs one message and cannot be made to sign another. See
 * [FrostSigningSession] for the two rules that hold that in place; both are
 * enforced here, in [acceptProposal] and in [record].
 *
 * ### One approval, not three
 *
 * A DKG asks three times because each step publishes something different and
 * commits the member to something different. Here every step serves one
 * decision -- sign this event or do not -- and the event is fixed before the
 * member is asked. A second prompt would be the same question again.
 */
object FrostSigningManager {
    private const val TAG = "FrostSigningManager"

    private val logger = Logger.withTag(TAG)

    /**
     * Opens a signing session, making this device the coordinator.
     *
     * [unsignedEvent] is signed as it stands apart from its `pubkey`, which is
     * replaced with the group's key: a signature over an event claiming
     * somebody else's author would verify against nothing.
     */
    suspend fun proposeSigning(
        database: MantraDatabase,
        localChatRoom: LocalChatRoom,
        userPublicKey: HexKey,
        kind: Kind,
        tags: Array<Array<String>>,
        content: String,
        createdAt: Long = Clock.System.now().epochSeconds
    ): FrostSigningSession {
        val key = completedKey(database, localChatRoom.chatRoom.id)
            ?: throw IllegalStateException("This group has no shared key to sign with")

        val signerId = signerIdOf(database, key, userPublicKey)
            ?: throw IllegalStateException("This device is not a participant in ceremony ${key.id}")

        val unsignedEvent = unsignedEventOf(key, kind, tags, content, createdAt)
        val sessionId = RandomInstance.bytes(32).toHex()

        val session = FrostSigningSession(
            id = sessionId,
            chatRoomId = localChatRoom.chatRoom.id,
            coordinatorPublicKey = userPublicKey,
            userPublicKey = userPublicKey,
            dkgSessionId = key.id,
            threshold = key.threshold,
            participantCount = key.participantCount,
            signerId = signerId,
            unsignedEventJson = unsignedEvent.toJson(),
            eventId = unsignedEvent.id,
            // Fresh per session, and never reused: this is the seed the device's
            // secret nonce is regenerated from for the life of the session.
            nonceRandom = RandomInstance.bytes(32).toHex(),
            // Proposing a signature is already the act of agreeing to it.
            signApprovedAt = Clock.System.now()
        )
        database.frostSigningSessionDao().upsert(session)
        announceStarted(database, session)

        logger.i("Proposing signature $sessionId over event ${unsignedEvent.id} with key ${key.id}")

        broadcast(
            database = database,
            localChatRoom = localChatRoom,
            session = session,
            kind = FrostSigningEvents.PROPOSAL,
            content = session.unsignedEventJson,
            includeKey = true
        )

        advance(database, localChatRoom, sessionId)

        return session
    }

    /**
     * Feeds one inbound signing message in and advances the session as far as it
     * will go. Safe to call twice with the same message: every write is keyed
     * and every step recomputed from stored inputs.
     */
    suspend fun processSigningPayload(
        database: MantraDatabase,
        localChatRoom: LocalChatRoom,
        innerEvent: Event,
        userPublicKey: HexKey
    ) {
        val sessionId = FrostSigningEvents.parseSessionId(innerEvent.tags)
        if (sessionId == null) {
            logger.w("FROST payload ${innerEvent.id} has no session tag; dropping")
            return
        }

        val session = if (innerEvent.kind == FrostSigningEvents.PROPOSAL) {
            acceptProposal(
                database = database,
                localChatRoom = localChatRoom,
                innerEvent = innerEvent,
                sessionId = sessionId,
                userPublicKey = userPublicKey
            )
        } else {
            // Not knowing the session is normal: a member catching up applies a
            // group's backlog in whatever order the epochs decrypt, so a nonce can
            // land before the proposal that asks for it. The inner event is already
            // stored, and [acceptProposal] replays it once the proposal turns up.
            database.frostSigningSessionDao().getSessionById(sessionId)
        }

        if (session == null) {
            logger.i("No signing session $sessionId for kind ${innerEvent.kind}; leaving it stored")
            return
        }

        if (session.stage == FrostSigningStage.FAILED) {
            logger.i("Session $sessionId already failed; ignoring kind ${innerEvent.kind}")
            return
        }

        if (!record(database, session, innerEvent)) return

        advance(database, localChatRoom, session.id)
    }

    /**
     * Records a proposal. Returns the session, whether it was just created or
     * already known.
     *
     * Publishes nothing: a signature is the one thing a group's key exists to
     * produce, so a relay delivering an event to a phone in a pocket must not be
     * enough to make it happen.
     */
    private suspend fun acceptProposal(
        database: MantraDatabase,
        localChatRoom: LocalChatRoom,
        innerEvent: Event,
        sessionId: String,
        userPublicKey: HexKey
    ): FrostSigningSession? {
        database.frostSigningSessionDao().getSessionById(sessionId)?.let { existing ->
            // The message a session signs is fixed at creation. A second proposal
            // under the same id carrying a different event is either a mistake or
            // an attempt to get two signatures out of one secret nonce, which is
            // how a share is extracted -- so it is refused, not applied.
            val proposed = Event.fromJsonOrNull(innerEvent.content)
            if (proposed != null && proposed.id != existing.eventId) {
                logger.w(
                    "Session $sessionId re-proposed with event ${proposed.id}, " +
                        "but it is already signing ${existing.eventId}; ignoring"
                )
            }
            return existing
        }

        val dkgSessionId = FrostSigningEvents.parseKey(innerEvent.tags)
        if (dkgSessionId == null) {
            logger.w("Signing proposal $sessionId names no key; dropping")
            return null
        }

        val key = database.dkgSessionDao().getSessionById(dkgSessionId)
        if (key == null || key.stage != DkgRitualStage.COMPLETE) {
            logger.w("Signing proposal $sessionId names key $dkgSessionId, which this device does not hold; dropping")
            return null
        }

        val signerId = signerIdOf(database, key, userPublicKey)
        if (signerId == null) {
            logger.w("Signing proposal $sessionId is for a ceremony this device did not take part in; dropping")
            return null
        }

        val proposed = Event.fromJsonOrNull(innerEvent.content)
        if (proposed == null) {
            logger.w("Signing proposal $sessionId does not carry an event; dropping")
            return null
        }

        // Rebuilt from the event's own fields rather than trusted. The id is the
        // 32 bytes every signer puts their share behind, so taking the proposer's
        // word for it would let them have the group sign one thing while being
        // shown another.
        val unsignedEvent = unsignedEventOf(
            key = key,
            kind = proposed.kind,
            tags = proposed.tags,
            content = proposed.content,
            createdAt = proposed.createdAt
        )
        if (unsignedEvent.id != proposed.id) {
            logger.w(
                "Signing proposal $sessionId carries id ${proposed.id} but its fields hash " +
                    "to ${unsignedEvent.id}; dropping"
            )
            return null
        }

        val session = FrostSigningSession(
            id = sessionId,
            chatRoomId = localChatRoom.chatRoom.id,
            // Whoever proposes coordinates. Aggregating nonces and partial
            // signatures gives no power over the outcome -- a wrong aggregate
            // produces a signature that does not verify, not a forged one.
            coordinatorPublicKey = innerEvent.pubKey,
            userPublicKey = userPublicKey,
            dkgSessionId = key.id,
            threshold = key.threshold,
            participantCount = key.participantCount,
            signerId = signerId,
            unsignedEventJson = unsignedEvent.toJson(),
            eventId = unsignedEvent.id,
            nonceRandom = RandomInstance.bytes(32).toHex()
        )
        database.frostSigningSessionDao().upsert(session)
        announceStarted(database, session)

        logger.i("Recorded signing session $sessionId over ${unsignedEvent.id}; awaiting approval")

        announceApprovalNeeded(database, session)
        replayStoredMessages(database, session)

        return session
    }

    /**
     * Feeds in every message of this session that arrived before the proposal did.
     *
     * They were dropped at the time for want of a session to file them under, but
     * the inbound path stores every payload it decrypts before dispatching on
     * kind, so nothing was actually lost — this reads them back out.
     */
    private suspend fun replayStoredMessages(
        database: MantraDatabase,
        session: FrostSigningSession
    ) {
        val stored = database.marmotInnerEventDao().getByChatRoomAndKinds(
            chatRoomId = session.chatRoomId,
            kinds = FrostSigningEvents.ALL.toList()
        ).filter { stored ->
            stored.kind != FrostSigningEvents.PROPOSAL &&
                FrostSigningEvents.parseSessionId(stored.tags) == session.id
        }.map { stored ->
            Event(
                id = stored.id,
                pubKey = stored.publicKey,
                createdAt = stored.createdAt.epochSeconds,
                kind = stored.kind,
                tags = stored.tags,
                content = stored.content,
                sig = ""
            )
        }

        if (stored.isEmpty()) return

        logger.i("Replaying ${stored.size} stored message(s) for signing session ${session.id}")

        stored.forEach { innerEvent ->
            if (!record(database, session, innerEvent)) return
        }
    }

    /**
     * Files one signing message. Returns false when the message ends the session,
     * so the caller stops rather than trying to advance a dead one.
     */
    private suspend fun record(
        database: MantraDatabase,
        session: FrostSigningSession,
        innerEvent: Event
    ): Boolean {
        when (innerEvent.kind) {
            FrostSigningEvents.PROPOSAL -> Unit // handled by acceptProposal

            FrostSigningEvents.NONCE,
            FrostSigningEvents.PARTIAL_SIGNATURE -> {
                val known = database.frostSigningSessionDao()
                    .getMessage(session.id, innerEvent.kind, innerEvent.pubKey) != null

                database.frostSigningSessionDao().upsert(
                    FrostSignerMessage(
                        sessionId = session.id,
                        signerPublicKey = innerEvent.pubKey,
                        kind = innerEvent.kind,
                        payload = innerEvent.content
                    )
                )

                if (!known) {
                    announceStep(database, session, innerEvent.kind, innerEvent.pubKey)
                }
            }

            FrostSigningEvents.SIGNER_SET -> {
                if (!isFromCoordinator(session, innerEvent)) return true

                val signerIds = FrostSigningEvents.parseSignerIds(innerEvent.tags)
                if (signerIds == null) {
                    logger.w("Session ${session.id}: signer set carries no ids; ignoring")
                    return true
                }

                val known = current(database, session).aggregatedNonce != null

                // Write-once, and this is the load-bearing one. Signing the same
                // message twice under one secret nonce against two different
                // aggregated nonces is exactly how a secret share is extracted, so
                // a coordinator that sends a second, different signer set is
                // ignored rather than obeyed. The session stalls; the share does
                // not leak.
                update(database, session) { current ->
                    if (current.aggregatedNonce == null) {
                        current.copy(
                            aggregatedNonce = innerEvent.content,
                            signerIds = signerIds.joinToString(",")
                        )
                    } else {
                        current
                    }
                }

                if (!known) {
                    announceStep(database, session, innerEvent.kind, innerEvent.pubKey)
                }
            }

            FrostSigningEvents.SIGNATURE -> {
                if (!isFromCoordinator(session, innerEvent)) return true

                val known = current(database, session).signature != null

                update(database, session) { current ->
                    if (current.signature == null) {
                        current.copy(signature = innerEvent.content)
                    } else {
                        current
                    }
                }

                if (!known) {
                    announceStep(database, session, innerEvent.kind, innerEvent.pubKey)
                }
            }

            FrostSigningEvents.FAILURE -> {
                fail(
                    database = database,
                    session = session,
                    reason = "Abandoned by ${innerEvent.pubKey.take(8)}: ${innerEvent.content}",
                    culprit = innerEvent.pubKey
                )
                return false
            }
        }

        return true
    }

    private fun isFromCoordinator(
        session: FrostSigningSession,
        innerEvent: Event
    ): Boolean {
        if (innerEvent.pubKey == session.coordinatorPublicKey) return true

        logger.w(
            "Session ${session.id}: kind ${innerEvent.kind} from " +
                "${innerEvent.pubKey.take(8)}, who is not the coordinator; ignoring"
        )
        return false
    }

    /**
     * Takes every step the stored messages now allow, in order, stopping at the
     * first one still waiting on somebody.
     *
     * Each step asks whether its output already exists rather than whether the
     * stage says it has run, so re-entering after a crash — or after the same
     * message is delivered twice — repeats no work and skips none.
     */
    private suspend fun advance(
        database: MantraDatabase,
        localChatRoom: LocalChatRoom,
        sessionId: String
    ) {
        var session = database.frostSigningSessionDao().getSessionById(sessionId) ?: return
        if (session.stage == FrostSigningStage.COMPLETE || session.stage == FrostSigningStage.FAILED) return

        val key = database.dkgSessionDao().getSessionById(session.dkgSessionId) ?: return
        val secretShare = key.secretShare?.let { PrivateKey(ByteVector32(it)) } ?: return
        val thresholdPublicKey = key.thresholdPublicKey?.let { PublicKey(ByteVector(it.hexToByteArray())) }
            ?: return

        try {
            // Nothing of this device's own goes out before its owner has said so.
            // Returns rather than throws: the session is not failing, it is waiting
            // on a person, and everything received stays stored so it resumes the
            // moment they approve.
            if (session.signApprovedAt == null) {
                announceApprovalNeeded(database, session)
                return
            }

            val tweakCache = TweakCache.create(thresholdPublicKey)
            val message = ByteVector(session.eventId.hexToByteArray())
            val publicShares = key.publicShareList()

            // Regenerated rather than stored -- SecretNonce refuses both. Safe
            // because the session's message can never change; see the notes on
            // FrostSigningSession.
            val (secretNonce, publicNonce) = SecretNonce.generate(
                sessionRandom = ByteVector32(session.nonceRandom),
                secretShare = secretShare,
                publicShare = publicShares?.getOrNull(session.signerId),
                tweakedThresholdPublicKey = tweakCache.tweakedPublicKey,
                message = message,
                extraInput = null
            )

            if (ownMessage(database, session, FrostSigningEvents.NONCE) == null) {
                publishOwn(
                    database,
                    localChatRoom,
                    session,
                    FrostSigningEvents.NONCE,
                    publicNonce.data.toHex()
                )
            }

            if (session.isCoordinator() && session.aggregatedNonce == null) {
                val offered = orderedNonces(database, session) ?: return
                val chosen = offered.take(session.threshold)

                val aggregated = IndividualNonce.aggregate(chosen.map { it.second })
                    .orThrow("aggregating nonces")

                session = update(database, session) {
                    it.copy(
                        aggregatedNonce = aggregated.toByteArray().toHex(),
                        signerIds = chosen.joinToString(",") { (id, _) -> id.toString() }
                    )
                }
                broadcast(
                    database = database,
                    localChatRoom = localChatRoom,
                    session = session,
                    kind = FrostSigningEvents.SIGNER_SET,
                    content = aggregated.toByteArray().toHex(),
                    signerIds = chosen.map { (id, _) -> id }
                )
                announceStep(
                    database,
                    session,
                    FrostSigningEvents.SIGNER_SET,
                    session.userPublicKey
                )
            }

            val aggregatedNonce = session.aggregatedNonce ?: return
            val signerIds = session.signerIdList() ?: return
            session = moveTo(database, session, FrostSigningStage.COLLECTING_PARTIAL_SIGNATURES)

            val signingSession = Session.create(
                aggregatedNonce = AggregatedNonce(aggregatedNonce.hexToByteArray()),
                signerIds = signerIds.map { it.toUInt() },
                signerPublicShares = publicShares?.let { shares ->
                    signerIds.mapNotNull { shares.getOrNull(it) }.takeIf { it.size == signerIds.size }
                },
                nParticipants = session.participantCount,
                threshold = session.threshold,
                tweakCache = tweakCache,
                message = message
            )

            // A member outside the chosen set has nothing to contribute and is not
            // holding anybody up. They stay in the session to receive the finished
            // signature like everybody else.
            if (session.isSigner() && ownMessage(database, session, FrostSigningEvents.PARTIAL_SIGNATURE) == null) {
                val partialSignature = signingSession
                    .sign(secretNonce, secretShare, session.signerId.toUInt())
                    .orThrow("signing")

                publishOwn(
                    database,
                    localChatRoom,
                    session,
                    FrostSigningEvents.PARTIAL_SIGNATURE,
                    partialSignature.toHex()
                )
            }

            if (session.isCoordinator() && session.signature == null) {
                val partials = orderedPartialSignatures(database, session, signerIds) ?: return

                val signature = signingSession
                    .aggregateSigs(partials.map { ByteVector32(it) })
                    .orThrow("aggregating partial signatures")
                    .toHex()

                session = update(database, session) { it.copy(signature = signature) }
                broadcast(
                    database = database,
                    localChatRoom = localChatRoom,
                    session = session,
                    kind = FrostSigningEvents.SIGNATURE,
                    content = signature
                )
                announceStep(database, session, FrostSigningEvents.SIGNATURE, session.userPublicKey)
            }

            val signature = session.signature ?: return

            // The payoff: a signature that verifies is one the group made, whoever
            // relayed it. Checking rather than trusting is what keeps a faulty or
            // dishonest coordinator from passing off something that will be
            // rejected by every relay it reaches.
            val signedEvent = signedEvent(session, signature)
            val verified = Nip01Crypto.verify(
                signature = signature.hexToByteArray(),
                hash = session.eventId.hexToByteArray(),
                pubKey = signedEvent.pubKey.hexToByteArray()
            )
            if (!verified) {
                throw IllegalStateException("The aggregated signature does not verify against ${session.eventId}")
            }

            update(database, session) { it.copy(stage = FrostSigningStage.COMPLETE) }

            // A signature exists to be used. Every device has the event and the
            // signature by now, so each applies the result itself rather than
            // waiting to be sent something it can already build -- the same
            // reasoning the transcript lines are written on. Nothing goes on the
            // wire: a signed event authored by the threshold key cannot travel as
            // an inner event anyway, because the outbound pipeline re-authors
            // rumors as their sender and would strip the group's signature off.
            applySignedEvent(database, session, signedEvent)

            announce(
                database = database,
                session = session,
                messageType = ChatMessage.TYPE_FROST_COMPLETE,
                content = "The group signed the event. It took ${session.threshold} of " +
                    "${session.participantCount} members.",
                actor = session.coordinatorPublicKey
            )

            logger.i("Signing session $sessionId complete")
        } catch (e: CancellationException) {
            // The sync was torn down mid-step, which says nothing about the session.
            throw e
        } catch (e: Throwable) {
            logger.e("Signing session $sessionId failed", e)
            fail(database, session, e.message ?: e::class.simpleName ?: "Unknown error")
            broadcast(
                database = database,
                localChatRoom = localChatRoom,
                session = session,
                kind = FrostSigningEvents.FAILURE,
                content = e.message ?: "Signing failed"
            )
        }
    }

    /**
     * Turns the signed event into whatever it is: a dialect, an artifact, a
     * chapter.
     *
     * Reuses the inbound path's dispatch rather than repeating it, with no group
     * event and no inner event behind the row -- there is neither, and both
     * columns are nullable for exactly this kind of locally-derived record.
     *
     * A failure here is not the session's: the signature is made and valid, and
     * saying otherwise would tell the group to abandon a ceremony that
     * succeeded. It is logged and the session still completes.
     */
    private suspend fun applySignedEvent(
        database: MantraDatabase,
        session: FrostSigningSession,
        signedEvent: Event
    ) {
        try {
            ChatMessage.applyInnerEvent(
                database = database,
                groupId = session.chatRoomId,
                event = signedEvent,
                marmotGroupEventId = null,
                marmotInnerEventId = null,
                senderPublicKey = session.coordinatorPublicKey,
                isUserMessage = session.isCoordinator(),
                createdAt = Clock.System.now()
            )?.let { database.chatMessageDao().upsert(it) }
        } catch (e: Throwable) {
            logger.e("Signed ${signedEvent.id} but could not apply it locally", e)
        }
    }

    /**
     * The event this session produces, with the signature on it.
     *
     * Public so a caller can take the finished event and do whatever it was
     * signing it for — the session's job ends at a valid signature.
     */
    fun signedEvent(session: FrostSigningSession, signature: HexKey): Event {
        val unsigned = Event.fromJson(session.unsignedEventJson)

        return Event(
            id = unsigned.id,
            pubKey = unsigned.pubKey,
            createdAt = unsigned.createdAt,
            kind = unsigned.kind,
            tags = unsigned.tags,
            content = unsigned.content,
            sig = signature
        )
    }

    /** The finished event, or null while the session is still running. */
    fun signedEvent(session: FrostSigningSession): Event? =
        session.signature?.let { signedEvent(session, it) }

    /**
     * The nonces on offer, as (signer id, nonce), ordered by signer id — or null
     * while fewer than the threshold have arrived.
     *
     * Ordered so that every device that recomputes the aggregate from the same
     * set arrives at the same bytes. FROST binds the signer set into the
     * challenge, so an order nobody else derives is a signature nobody can
     * aggregate.
     */
    private suspend fun orderedNonces(
        database: MantraDatabase,
        session: FrostSigningSession
    ): List<Pair<Int, IndividualNonce>>? {
        val key = database.dkgSessionDao().getSessionById(session.dkgSessionId) ?: return null
        val idByMember = signerIds(database, key)

        val offered = database.frostSigningSessionDao()
            .getMessagesByKind(session.id, FrostSigningEvents.NONCE)
            .mapNotNull { message ->
                idByMember[message.signerPublicKey]?.let { id ->
                    id to IndividualNonce(message.payload.hexToByteArray())
                }
            }
            .sortedBy { (id, _) -> id }

        if (offered.size < session.threshold) {
            logger.d("Session ${session.id}: ${offered.size}/${session.threshold} nonces")
            return null
        }

        return offered
    }

    /**
     * The chosen signers' partial signatures in signer-set order, or null while
     * any are missing. Aggregation needs exactly one per signer, positionally
     * matched to the set the session was created with.
     */
    private suspend fun orderedPartialSignatures(
        database: MantraDatabase,
        session: FrostSigningSession,
        signerIds: List<Int>
    ): List<ByteArray>? {
        val key = database.dkgSessionDao().getSessionById(session.dkgSessionId) ?: return null
        val memberById = signerIds(database, key).entries.associate { (member, id) -> id to member }

        val payloadByMember = database.frostSigningSessionDao()
            .getMessagesByKind(session.id, FrostSigningEvents.PARTIAL_SIGNATURE)
            .associate { it.signerPublicKey to it.payload }

        val partials = signerIds.mapNotNull { id ->
            memberById[id]?.let { payloadByMember[it] }
        }

        if (partials.size < signerIds.size) {
            logger.d("Session ${session.id}: ${partials.size}/${signerIds.size} partial signatures")
            return null
        }

        return partials.map { it.hexToByteArray() }
    }

    /**
     * Every ceremony participant's FROST id, keyed by their nostr public key.
     *
     * The id is the member's index in the ceremony's participant order, which is
     * the bytewise sort of the host public keys — the same ordering ChillDKG
     * hashed into the session identity, and so the same one the public shares
     * are in. Deriving it rather than storing it means signing cannot disagree
     * with the ceremony that made the key.
     */
    private suspend fun signerIds(
        database: MantraDatabase,
        key: DkgSession
    ): Map<HexKey, Int> {
        val hostKeys = database.dkgSessionDao()
            .getMessagesByKind(key.id, DkgRitualEvents.HOST_KEY)
            .associate { it.participantPublicKey to it.payload.lowercase() }

        val order = hostKeys.values.sorted()

        return hostKeys.mapNotNull { (member, hostKey) ->
            order.indexOf(hostKey).takeIf { it >= 0 }?.let { member to it }
        }.toMap()
    }

    private suspend fun signerIdOf(
        database: MantraDatabase,
        key: DkgSession,
        member: HexKey
    ): Int? = signerIds(database, key)[member]

    /**
     * The key a room signs with, or null when it has none.
     *
     * Signing runs in the #admins room, which is not where the ceremony ran. A
     * ceremony needs a NIP-17 group -- every member an equal admin, no MLS tree
     * to be outside of -- while a group event needs an MLS one, so the two
     * cannot be the same room.
     *
     * They are bound together by the room's [GroupKeyState]: the announcement
     * the room opened with, naming the ceremony behind it. That is a lookup
     * rather than a search, and it carries the derivation path, so a room
     * derived anywhere other than [SharedKeyDerivation.MARMOT_ADMIN_GROUP_PATH]
     * is findable at all -- which the rederivation below cannot manage, since it
     * can only rederive at the one path the constant names.
     *
     * The state buys none of its authority from being written down. It is only
     * ever stored having rederived the room it describes, so what actually binds
     * a room to a key is still that the room's id *is* the key, and a room still
     * cannot be pointed at a key it was not derived from.
     *
     * Two fallbacks behind it, both for rooms that predate the table: the
     * original scan over every ceremony this device holds a share for, and then
     * a ceremony held in this very room, which is not how the app wires things
     * today but costs one lookup to keep honest.
     */
    suspend fun completedKey(database: MantraDatabase, chatRoomId: String): DkgSession? {
        GroupKeyStateManager.keyStateFor(database, chatRoomId)?.let { state ->
            database.dkgSessionDao().getSessionById(state.dkgSessionId)?.takeIf { key ->
                key.stage == DkgRitualStage.COMPLETE &&
                    key.secretShare != null &&
                    key.thresholdPublicKey == state.thresholdPublicKey
            }?.let { return it }
        }

        database.dkgSessionDao().getKeyHoldingSessions().firstOrNull { session ->
            session.stage == DkgRitualStage.COMPLETE &&
                session.thresholdPublicKey?.let {
                    SharedKeyDerivation.marmotGroupId(it) == chatRoomId
                } == true
        }?.let { return it }

        return database.dkgSessionDao()
            .getLatestSessionForChatRoom(chatRoomId)
            ?.takeIf { it.stage == DkgRitualStage.COMPLETE && it.secretShare != null }
    }

    /** Whether this group can sign at all, read by the UI so it offers nothing that would fail. */
    suspend fun canSign(database: MantraDatabase, chatRoomId: String): Boolean =
        completedKey(database, chatRoomId) != null

    /**
     * The unsigned event a session signs: the caller's fields under the group's
     * key, with the id computed from them.
     */
    private fun unsignedEventOf(
        key: DkgSession,
        kind: Kind,
        tags: Array<Array<String>>,
        content: String,
        createdAt: Long
    ): Event {
        // Nostr identifies an author by the x-only key a BIP-340 signature
        // verifies against, which for a FROST key is the tweaked threshold key
        // rather than the 33-byte value the ceremony reports.
        val thresholdPublicKey = PublicKey(ByteVector(key.thresholdPublicKey!!.hexToByteArray()))
        val groupPubKey = TweakCache.create(thresholdPublicKey).tweakedPublicKey.value.toHex()

        return Event(
            id = EventHasher.hashId(
                pubKey = groupPubKey,
                createdAt = createdAt,
                kind = kind,
                tags = tags,
                content = content
            ),
            pubKey = groupPubKey,
            createdAt = createdAt,
            kind = kind,
            tags = tags,
            content = content,
            sig = ""
        )
    }

    /**
     * Turns a failed FROST step into an exception so it joins [advance]'s single
     * failure path.
     *
     * The library reports these as `Either`, because a partial signature that
     * will not aggregate is a normal outcome of collecting them from other
     * people rather than a bug. This session has one response to all of them —
     * there is no signature, so it dies and the group is told — and what is
     * worth keeping is which step failed.
     */
    private fun <T> Either<Throwable, T>.orThrow(step: String): T = when (this) {
        is Either.Right -> value
        is Either.Left -> throw IllegalStateException("FROST $step failed: ${value.message}", value)
    }

    /** This device's own message of [kind], if it has published one. */
    private suspend fun ownMessage(
        database: MantraDatabase,
        session: FrostSigningSession,
        kind: Kind
    ): FrostSignerMessage? = database.frostSigningSessionDao().getMessage(
        sessionId = session.id,
        kind = kind,
        signerPublicKey = session.userPublicKey
    )

    /**
     * Applies [transform] to the *stored* session and returns what was written.
     *
     * Callers hold a session across several steps, each of which may write; going
     * back to the row means a later write cannot silently undo an earlier one by
     * copying from a stale snapshot.
     */
    private suspend fun update(
        database: MantraDatabase,
        session: FrostSigningSession,
        transform: (FrostSigningSession) -> FrostSigningSession
    ): FrostSigningSession {
        val current = database.frostSigningSessionDao().getSessionById(session.id) ?: session
        val updated = transform(current)

        if (updated == current) return current

        val stamped = updated.copy(updatedAt = Clock.System.now())
        database.frostSigningSessionDao().upsert(stamped)

        return stamped
    }

    /**
     * Moves the progress label forward, never back. Stages are declared in the
     * order the session runs them, so a message arriving out of order cannot
     * walk the UI back down the ladder.
     */
    private suspend fun moveTo(
        database: MantraDatabase,
        session: FrostSigningSession,
        stage: FrostSigningStage
    ): FrostSigningSession = update(database, session) { current ->
        val settled =
            current.stage == FrostSigningStage.COMPLETE || current.stage == FrostSigningStage.FAILED

        if (settled || current.stage.ordinal >= stage.ordinal) current else current.copy(stage = stage)
    }

    private suspend fun fail(
        database: MantraDatabase,
        session: FrostSigningSession,
        reason: String,
        culprit: HexKey = session.userPublicKey
    ) {
        // Read before writing so the notice goes out once. A session can be failed
        // from two directions -- a FAILURE message from a member, and a fault
        // raised locally -- and the group does not need to be told twice.
        val current = database.frostSigningSessionDao().getSessionById(session.id) ?: session
        if (current.stage == FrostSigningStage.FAILED) return

        update(database, current) {
            it.copy(stage = FrostSigningStage.FAILED, failureReason = reason)
        }
        announce(
            database = database,
            session = current,
            messageType = ChatMessage.TYPE_FROST_FAILED,
            content = "abandoned the signature. Nothing was signed, and it is safe to ask again.",
            actor = culprit
        )
    }

    /** The stored session, for reading a column back before overwriting it. */
    private suspend fun current(
        database: MantraDatabase,
        session: FrostSigningSession
    ): FrostSigningSession =
        database.frostSigningSessionDao().getSessionById(session.id) ?: session

    /**
     * Whether this session is waiting on its owner to agree to sign.
     *
     * Mirrors the gate in [advance]: pending exactly when [advance] would stop at
     * it. The two must agree, or the screen offers an approval that does nothing,
     * or none while the session sits still.
     */
    fun isAwaitingApproval(session: FrostSigningSession): Boolean {
        if (session.stage == FrostSigningStage.COMPLETE || session.stage == FrostSigningStage.FAILED) {
            return false
        }

        return session.signApprovedAt == null
    }

    /**
     * Records that this device's owner agreed to sign, then lets the session run
     * as far as the next thing it is waiting on.
     *
     * Approving a session not waiting on it is a no-op rather than an error: a
     * stale screen left open across a redelivery should not publish anything.
     */
    suspend fun approve(
        database: MantraDatabase,
        localChatRoom: LocalChatRoom,
        sessionId: String
    ) {
        val session = database.frostSigningSessionDao().getSessionById(sessionId) ?: return

        if (!isAwaitingApproval(session)) {
            logger.i("Session $sessionId is not waiting on an approval; ignoring it")
            return
        }

        update(database, session) { it.copy(signApprovedAt = Clock.System.now()) }

        logger.i("Session $sessionId: signing approved")

        advance(database, localChatRoom, sessionId)
    }

    /**
     * Refuses to sign, and tells the group so the coordinator can pick somebody
     * else rather than wait.
     *
     * A t-of-n group can sign without this member, so declining is a normal
     * outcome and not a failure of the session — but only if it is said out loud.
     * Silence is indistinguishable from a phone in a pocket.
     */
    suspend fun decline(
        database: MantraDatabase,
        localChatRoom: LocalChatRoom,
        sessionId: String
    ) {
        val session = database.frostSigningSessionDao().getSessionById(sessionId) ?: return

        if (!isAwaitingApproval(session)) return

        fail(
            database = database,
            session = session,
            reason = "Declined on this device",
            culprit = session.userPublicKey
        )
        broadcast(
            database = database,
            localChatRoom = localChatRoom,
            session = session,
            kind = FrostSigningEvents.FAILURE,
            content = "Declined to sign"
        )
    }

    private suspend fun announceStarted(database: MantraDatabase, session: FrostSigningSession) =
        announce(
            database = database,
            session = session,
            messageType = ChatMessage.TYPE_FROST_STARTED,
            content = "asked the group to sign something with its shared key. It takes " +
                "${session.threshold} of ${session.participantCount} members to do it.",
            actor = session.coordinatorPublicKey
        )

    /**
     * Tells the group's chat that this session is waiting on the reader.
     *
     * Written once, guarded by [FrostSigningSession.approvalRequestedAt], because
     * [advance] runs on every arriving message and would otherwise ask again on
     * each one.
     */
    private suspend fun announceApprovalNeeded(
        database: MantraDatabase,
        session: FrostSigningSession
    ) {
        if (current(database, session).approvalRequestedAt != null) return

        update(database, session) { it.copy(approvalRequestedAt = Clock.System.now()) }

        announce(
            database = database,
            session = session,
            messageType = ChatMessage.TYPE_FROST_APPROVAL_NEEDED,
            content = "Your approval is needed to sign with the group's shared key. Nothing " +
                "has been published from this device yet.",
            actor = session.userPublicKey
        )
    }

    /**
     * Puts one protocol message in the group's chat, as a line naming who sent it.
     *
     * Same reasoning as the ceremony's transcript: these are things a particular
     * member's device did, and a signature made with the group's key is worth
     * being able to watch. The wording describes what a step accomplishes rather
     * than what it is called.
     */
    private suspend fun announceStep(
        database: MantraDatabase,
        session: FrostSigningSession,
        kind: Kind,
        actor: HexKey
    ) {
        val (messageType, content) = when (kind) {
            FrostSigningEvents.NONCE -> ChatMessage.TYPE_FROST_NONCE to
                "offered to help sign, sending the one-time value their signature needs."

            FrostSigningEvents.SIGNER_SET -> ChatMessage.TYPE_FROST_SIGNER_SET to
                "chose who is signing and combined their one-time values."

            FrostSigningEvents.PARTIAL_SIGNATURE -> ChatMessage.TYPE_FROST_PARTIAL_SIGNATURE to
                "signed their part. On its own it proves nothing; combined with the " +
                    "others it is the group's signature."

            FrostSigningEvents.SIGNATURE -> ChatMessage.TYPE_FROST_SIGNATURE to
                "combined the parts into the group's signature."

            // PROPOSAL and FAILURE are announced by the code that acts on them --
            // both say more than the message itself carries.
            else -> return
        }

        announce(
            database = database,
            session = session,
            messageType = messageType,
            content = content,
            actor = actor
        )
    }

    /**
     * Puts a signing milestone in the group's chat.
     *
     * Each device writes its own row from the messages it has already received, so
     * this costs no traffic and cannot disagree with the session it describes.
     * Written once by construction rather than de-duplication: every caller checks
     * before it writes, because [ChatMessage] has no key to make a second insert a
     * no-op.
     */
    private suspend fun announce(
        database: MantraDatabase,
        session: FrostSigningSession,
        messageType: String,
        content: String,
        actor: HexKey
    ) {
        database.chatMessageDao().upsert(
            ChatMessage(
                senderPublicKey = actor,
                isUserMessage = actor == session.userPublicKey,
                giftWrapPayloadId = null,
                marmotGroupEventId = null,
                marmotInnerEventId = null,
                chatRoomId = session.chatRoomId,
                content = content,
                messageType = messageType
            )
        )
    }

    /**
     * Broadcasts one of this device's own protocol messages AND records it
     * locally. The local copy matters: the coordinator signs too, and its own
     * message has to be in the aggregation alongside everyone else's.
     *
     * Queued before it is recorded, because [advance] republishes any message it
     * has no local copy of. A crash between the two therefore costs a duplicate
     * broadcast — which every receiver folds away — rather than a message the
     * group waits on forever.
     */
    private suspend fun publishOwn(
        database: MantraDatabase,
        localChatRoom: LocalChatRoom,
        session: FrostSigningSession,
        kind: Kind,
        content: String
    ) {
        broadcast(database, localChatRoom, session, kind, content)

        val known = ownMessage(database, session, kind) != null

        database.frostSigningSessionDao().upsert(
            FrostSignerMessage(
                sessionId = session.id,
                signerPublicKey = session.userPublicKey,
                kind = kind,
                payload = content
            )
        )

        if (!known) announceStep(database, session, kind, session.userPublicKey)
    }

    /**
     * Queues a signing message as an unprocessed marmot inner event.
     * `NotaryViewModel` picks it up, MLS-encrypts it and broadcasts it as a
     * kind:445 for the room — the same path every other event in a Marmot group
     * takes, which is why this needs no transport of its own.
     *
     * No p-tags. A gift wrap is addressed and sealed once per recipient, so the
     * ceremony has to name everybody on every message; a group event is
     * encrypted to the group, and who is in it is the MLS tree's business rather
     * than the message's. That also means the signer set genuinely comes from
     * the ceremony rather than from whoever happened to be tagged.
     */
    private suspend fun broadcast(
        database: MantraDatabase,
        localChatRoom: LocalChatRoom,
        session: FrostSigningSession,
        kind: Kind,
        content: String,
        includeKey: Boolean = false,
        signerIds: List<Int>? = null
    ) {
        val tags = FrostSigningEvents.assembleTags(
            sessionId = session.id,
            dkgSessionId = if (includeKey) session.dkgSessionId else null,
            signerIds = signerIds
        )

        val createdAt = Clock.System.now().epochSeconds

        database.marmotInnerEventDao().upsert(
            MarmotInnerEvent(
                // The rumor id the outbound pipeline will recompute from these
                // same fields when it assembles the event to encrypt.
                id = EventHasher.hashId(
                    pubKey = session.userPublicKey,
                    createdAt = createdAt,
                    tags = tags,
                    content = content,
                    kind = kind
                ),
                publicKey = session.userPublicKey,
                kind = kind,
                createdAt = Instant.fromEpochSeconds(createdAt),
                tags = tags,
                content = content,
                chatRoomId = localChatRoom.chatRoom.id
            )
        )
    }
}
