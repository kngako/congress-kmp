package press.mantra.compose.managers

import press.mantra.compose.database.MantraDatabase
import press.mantra.compose.database.model.DkgParticipantMessage
import press.mantra.compose.database.model.DkgSession
import press.mantra.compose.database.model.GiftWrapPayload
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.database.model.types.DkgRitualStage
import press.mantra.compose.extensions.toHex
import press.mantra.compose.nostr.dkg.DkgRitualEvents
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Kind
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.utils.RandomInstance
import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.ByteVector64
import fr.acinq.bitcoin.Crypto
import fr.acinq.bitcoin.PrivateKey
import fr.acinq.bitcoin.PublicKey
import fr.acinq.bitcoin.crypto.dkg.chill.ChillDKG
import fr.acinq.secp256k1.ChilldkgFault
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Runs a ChillDKG ritual over a NIP-17 group.
 *
 * The group is the participant set, the room's creator is the coordinator, and
 * every protocol message travels as a gift-wrapped rumor on the kinds in
 * [DkgRitualEvents] — the same sealing and broadcast pipeline chat messages
 * already use, so there is no second transport to operate.
 *
 * The coordinator is a participant too. ChillDKG treats the coordinator as
 * untrusted (it relays but cannot learn secrets or bias the key), so being the
 * room's creator buys it no authority here — only the job of aggregating.
 *
 * ### Why this is driven by arriving messages
 *
 * Every step is a pure function of inputs this device has already stored, so
 * there is no long-lived in-memory session to lose. Each inbound message is
 * persisted and then the ritual is asked whether it can move; if the app dies
 * mid-round it picks up exactly where it left off on the next message.
 */
object ChillDkgRitualManager {
    private const val TAG = "ChillDkgRitualManager"

    private val logger = Logger.withTag(TAG)

    /**
     * Domain separator for deriving the ChillDKG host key. The host key must NOT
     * be the nostr identity key: reusing one secret across two protocols means a
     * flaw in either can reach the other. Deriving it from the same seed keeps it
     * recoverable from the wallet backup without being the same key.
     */
    private const val HOST_KEY_DERIVATION_TAG = "mantra/chilldkg/host-key/v1"

    /**
     * This device's long-term ChillDKG host secret key.
     *
     * Deterministic in the nostr secret key, so it survives a reinstall from the
     * wallet seed alone — ChillDKG requires the host secret key to recover a
     * session's outputs, and asking a chat user to back up a second secret is a
     * good way to lose keys.
     */
    fun deriveHostSecretKey(nostrPrivateKey: ByteArray): PrivateKey =
        PrivateKey(Crypto.sha256(HOST_KEY_DERIVATION_TAG.encodeToByteArray() + nostrPrivateKey))

    fun deriveHostPublicKey(nostrPrivateKey: ByteArray): PublicKey =
        ChillDKG.hostPublicKey(deriveHostSecretKey(nostrPrivateKey))

    /**
     * Opens a ritual. Only the room's creator should call this; every other
     * member joins by reacting to the proposal.
     */
    suspend fun proposeRitual(
        database: MantraDatabase,
        localChatRoom: LocalChatRoom,
        userPublicKey: HexKey,
        nostrPrivateKey: ByteArray,
        threshold: Int
    ): DkgSession {
        val sessionId = RandomInstance.bytes(32).toHex()
        val participantCount = localChatRoom.localParticipants.size

        val session = DkgSession(
            id = sessionId,
            chatRoomId = localChatRoom.chatRoom.id,
            coordinatorPublicKey = userPublicKey,
            userPublicKey = userPublicKey,
            threshold = threshold,
            participantCount = participantCount,
            hostPublicKey = deriveHostPublicKey(nostrPrivateKey).value.toHex(),
            // Fresh per session. Persisted because participantStep1/2 are pure in
            // it, which is what makes the whole ritual restart-safe.
            round1Random = RandomInstance.bytes(32).toHex(),
            round2AuxRandom = RandomInstance.bytes(32).toHex()
        )
        database.dkgSessionDao().upsert(session)

        logger.i("Proposing DKG ritual $sessionId: ${session.threshold}-of-$participantCount")

        broadcast(
            database = database,
            localChatRoom = localChatRoom,
            session = session,
            kind = DkgRitualEvents.PROPOSAL,
            content = "",
            includeThreshold = true
        )

        publishHostKey(database, localChatRoom, session)

        return session
    }

    /**
     * Feeds one inbound ritual message in and advances the ritual as far as it
     * will go. Safe to call twice with the same message: every write is keyed
     * and every step recomputed from stored inputs.
     */
    suspend fun processRitualPayload(
        database: MantraDatabase,
        localChatRoom: LocalChatRoom,
        giftWrapPayload: GiftWrapPayload,
        userPublicKey: HexKey,
        nostrPrivateKey: ByteArray
    ) {
        val sessionId = DkgRitualEvents.parseSessionId(giftWrapPayload.tags)
        if (sessionId == null) {
            logger.w("DKG payload ${giftWrapPayload.id} has no session tag; dropping")
            return
        }

        val session = when (giftWrapPayload.kind) {
            DkgRitualEvents.PROPOSAL -> acceptProposal(
                database = database,
                localChatRoom = localChatRoom,
                giftWrapPayload = giftWrapPayload,
                sessionId = sessionId,
                userPublicKey = userPublicKey,
                nostrPrivateKey = nostrPrivateKey
            )
            else -> database.dkgSessionDao().getSessionById(sessionId)
        }

        if (session == null) {
            // A message from a ritual this device never joined, or one it has
            // already torn down. Nothing sane to do with it.
            logger.w("No DKG session $sessionId for kind ${giftWrapPayload.kind}; dropping")
            return
        }

        if (session.stage == DkgRitualStage.FAILED) {
            logger.i("Ritual $sessionId already failed; ignoring kind ${giftWrapPayload.kind}")
            return
        }

        when (giftWrapPayload.kind) {
            DkgRitualEvents.PROPOSAL -> Unit // handled above; host key already published
            DkgRitualEvents.HOST_KEY,
            DkgRitualEvents.ROUND_1,
            DkgRitualEvents.ROUND_2 -> database.dkgSessionDao().upsert(
                DkgParticipantMessage(
                    sessionId = session.id,
                    participantPublicKey = giftWrapPayload.publicKey,
                    kind = giftWrapPayload.kind,
                    payload = giftWrapPayload.content
                )
            )
            DkgRitualEvents.COORDINATOR_ROUND_1 -> database.dkgSessionDao().upsert(
                session.copy(coordinatorRound1 = giftWrapPayload.content, updatedAt = Clock.System.now())
            )
            DkgRitualEvents.CERTIFICATE -> database.dkgSessionDao().upsert(
                session.copy(certificate = giftWrapPayload.content, updatedAt = Clock.System.now())
            )
            DkgRitualEvents.FAILURE -> {
                fail(database, session, "Abandoned by ${giftWrapPayload.publicKey.take(8)}: ${giftWrapPayload.content}")
                return
            }
        }

        advance(
            database = database,
            localChatRoom = localChatRoom,
            sessionId = session.id,
            nostrPrivateKey = nostrPrivateKey
        )
    }

    /**
     * Records a proposal and answers it with this device's host key. Returns the
     * session, whether it was just created or already known.
     */
    private suspend fun acceptProposal(
        database: MantraDatabase,
        localChatRoom: LocalChatRoom,
        giftWrapPayload: GiftWrapPayload,
        sessionId: String,
        userPublicKey: HexKey,
        nostrPrivateKey: ByteArray
    ): DkgSession? {
        database.dkgSessionDao().getSessionById(sessionId)?.let { return it }

        val threshold = DkgRitualEvents.parseThreshold(giftWrapPayload.tags)
        if (threshold == null) {
            logger.w("DKG proposal $sessionId carries no threshold; dropping")
            return null
        }

        // Only the room's creator may open a ritual. Anyone else proposing one is
        // trying to run a key generation the group did not ask for.
        if (giftWrapPayload.publicKey != localChatRoom.chatRoom.userPublicKey &&
            giftWrapPayload.publicKey != coordinatorOf(localChatRoom)
        ) {
            logger.w("DKG proposal $sessionId from non-coordinator ${giftWrapPayload.publicKey}; dropping")
            return null
        }

        val session = DkgSession(
            id = sessionId,
            chatRoomId = localChatRoom.chatRoom.id,
            coordinatorPublicKey = giftWrapPayload.publicKey,
            userPublicKey = userPublicKey,
            threshold = threshold,
            participantCount = localChatRoom.localParticipants.size,
            hostPublicKey = deriveHostPublicKey(nostrPrivateKey).value.toHex(),
            round1Random = RandomInstance.bytes(32).toHex(),
            round2AuxRandom = RandomInstance.bytes(32).toHex()
        )
        database.dkgSessionDao().upsert(session)

        logger.i("Joined DKG ritual $sessionId (${threshold}-of-${session.participantCount})")

        publishHostKey(database, localChatRoom, session)

        return session
    }

    /**
     * Takes whatever step the stored messages now allow. Called after every
     * inbound message; a no-op until a round is actually complete.
     */
    private suspend fun advance(
        database: MantraDatabase,
        localChatRoom: LocalChatRoom,
        sessionId: String,
        nostrPrivateKey: ByteArray
    ) {
        val session = database.dkgSessionDao().getSessionById(sessionId) ?: return
        if (session.stage == DkgRitualStage.COMPLETE || session.stage == DkgRitualStage.FAILED) return

        val hostSeckey = deriveHostSecretKey(nostrPrivateKey)

        try {
            val hostPublicKeys = hostPublicKeys(database, session) ?: return

            when (session.stage) {
                DkgRitualStage.COLLECTING_HOST_KEYS -> {
                    // Every host key is in, so the participant set is settled and
                    // round 1 can be computed. Publish ours and move on.
                    val pmsg1 = ChillDKG.participantStep1(
                        hostSecretKey = hostSeckey,
                        hostPublicKeys = hostPublicKeys,
                        threshold = session.threshold,
                        random = ByteVector32(session.round1Random)
                    ).message
                    val moved = session.copy(
                        stage = DkgRitualStage.COLLECTING_ROUND_1,
                        updatedAt = Clock.System.now()
                    )
                    database.dkgSessionDao().upsert(moved)
                    publishOwn(database, localChatRoom, moved, DkgRitualEvents.ROUND_1, pmsg1.toHex())

                    advance(database, localChatRoom, sessionId, nostrPrivateKey)
                }

                DkgRitualStage.COLLECTING_ROUND_1 -> {
                    if (session.isCoordinator()) aggregateRound1(database, localChatRoom, session, hostPublicKeys)

                    val coordinatorRound1 = database.dkgSessionDao()
                        .getSessionById(sessionId)?.coordinatorRound1 ?: return

                    val step2 = ChillDKG.participantStep2(
                        hostSecretKey = hostSeckey,
                        state = participantState1(hostSeckey, hostPublicKeys, session),
                        coordinatorMessage = ByteVector(coordinatorRound1.hexToByteArray()),
                        auxRand = ByteVector32(session.round2AuxRandom)
                    )
                    step2.fault.raiseIfFaulty("round 2")
                    val pmsg2 = step2.certEqSignature

                    val moved = session.copy(
                        stage = DkgRitualStage.COLLECTING_ROUND_2,
                        coordinatorRound1 = coordinatorRound1,
                        updatedAt = Clock.System.now()
                    )
                    database.dkgSessionDao().upsert(moved)
                    publishOwn(database, localChatRoom, moved, DkgRitualEvents.ROUND_2, pmsg2.toHex())

                    advance(database, localChatRoom, sessionId, nostrPrivateKey)
                }

                DkgRitualStage.COLLECTING_ROUND_2 -> {
                    if (session.isCoordinator()) aggregateRound2(database, localChatRoom, session, hostPublicKeys)

                    val certificate = database.dkgSessionDao()
                        .getSessionById(sessionId)?.certificate ?: return

                    finalize(database, session, hostPublicKeys, hostSeckey, certificate)
                }

                DkgRitualStage.COMPLETE, DkgRitualStage.FAILED -> Unit
            }
        } catch (e: Throwable) {
            // The session is over for this device. Either a protocol fault came
            // back from a ChillDKG step -- [raiseIfFaulty] turns those into
            // exceptions naming the culprit -- or a step rejected a local input.
            // Either way the key is not usable, so say so rather than leaving a
            // spinner running forever.
            logger.e("DKG ritual $sessionId failed", e)
            fail(database, session, e.message ?: e::class.simpleName ?: "Unknown error")
            broadcast(
                database = database,
                localChatRoom = localChatRoom,
                session = session,
                kind = DkgRitualEvents.FAILURE,
                content = e.message ?: "Ritual failed"
            )
        }
    }

    /** Coordinator: fold every round-1 message into `CoordinatorMsg1` and publish it. */
    private suspend fun aggregateRound1(
        database: MantraDatabase,
        localChatRoom: LocalChatRoom,
        session: DkgSession,
        hostPublicKeys: List<PublicKey>
    ) {
        if (session.coordinatorRound1 != null) return

        val pmsgs1 = orderedPayloads(database, session, DkgRitualEvents.ROUND_1) ?: return

        val step1 = coordinatorStep1(pmsgs1, hostPublicKeys, session)
        val cmsg1 = step1.message

        database.dkgSessionDao().upsert(
            session.copy(coordinatorRound1 = cmsg1.toHex(), updatedAt = Clock.System.now())
        )
        broadcast(
            database = database,
            localChatRoom = localChatRoom,
            session = session,
            kind = DkgRitualEvents.COORDINATOR_ROUND_1,
            content = cmsg1.toHex()
        )
    }

    /** Coordinator: fold every round-2 signature into the certificate and publish it. */
    private suspend fun aggregateRound2(
        database: MantraDatabase,
        localChatRoom: LocalChatRoom,
        session: DkgSession,
        hostPublicKeys: List<PublicKey>
    ) {
        if (session.certificate != null) return

        val cmsg1 = session.coordinatorRound1 ?: return
        val pmsgs2 = orderedPayloads(database, session, DkgRitualEvents.ROUND_2) ?: return

        // Rebuild the coordinator state from the round-1 messages rather than
        // holding it across rounds — same inputs, same state, and it survives a
        // restart between the two aggregations.
        val pmsgs1 = orderedPayloads(database, session, DkgRitualEvents.ROUND_1) ?: return
        val coordinatorState = coordinatorStep1(pmsgs1, hostPublicKeys, session).state

        val certified = ChillDKG.coordinatorFinalize(
            state = coordinatorState,
            certEqSignatures = pmsgs2.map { ByteVector64(it) },
            threshold = session.threshold
        )
        certified.fault.raiseIfFaulty("certificate")
        val cmsg2 = certified.certificate

        database.dkgSessionDao().upsert(
            session.copy(
                coordinatorRound1 = cmsg1,
                certificate = cmsg2.toHex(),
                updatedAt = Clock.System.now()
            )
        )
        broadcast(
            database = database,
            localChatRoom = localChatRoom,
            session = session,
            kind = DkgRitualEvents.CERTIFICATE,
            content = cmsg2.toHex()
        )
    }

    /** Verify the certificate and store the share. This is the ritual's payoff. */
    private suspend fun finalize(
        database: MantraDatabase,
        session: DkgSession,
        hostPublicKeys: List<PublicKey>,
        hostSeckey: PrivateKey,
        certificate: HexKey
    ) {
        val step2 = ChillDKG.participantStep2(
            hostSecretKey = hostSeckey,
            state = participantState1(hostSeckey, hostPublicKeys, session),
            coordinatorMessage = ByteVector((session.coordinatorRound1 ?: return).hexToByteArray()),
            auxRand = ByteVector32(session.round2AuxRandom)
        )
        step2.fault.raiseIfFaulty("round 2")

        val output = ChillDKG.participantFinalize(
            state = step2.state,
            certificate = ByteVector(certificate.hexToByteArray()),
            nParticipants = session.participantCount,
            threshold = session.threshold
        )
        output.fault.raiseIfFaulty("finalize")

        logger.i("DKG ritual ${session.id} complete")

        database.dkgSessionDao().upsert(
            session.copy(
                stage = DkgRitualStage.COMPLETE,
                certificate = certificate,
                thresholdPublicKey = output.thresholdPublicKey?.value?.toHex(),
                secretShare = output.secretShare?.value?.toHex(),
                recoveryData = output.recovery?.toHex(),
                updatedAt = Clock.System.now()
            )
        )
    }

    /**
     * The agreed participant set, or null while host keys are still outstanding.
     * Ordering is a bytewise sort of the host public keys: ChillDKG fails outright
     * if the participants disagree on the order, and sorting is the only ordering
     * every device can derive independently.
     *
     * ChillDKG has no session-params object: every step takes this list and the
     * threshold, and hashes them into the session identity itself.
     */
    private suspend fun hostPublicKeys(
        database: MantraDatabase,
        session: DkgSession
    ): List<PublicKey>? {
        val hostKeys = database.dkgSessionDao().getMessagesByKind(session.id, DkgRitualEvents.HOST_KEY)

        if (hostKeys.size < session.participantCount) {
            logger.d("Ritual ${session.id}: ${hostKeys.size}/${session.participantCount} host keys")
            return null
        }

        return hostKeys.map { it.payload }.sorted().map { PublicKey(ByteVector(it.hexToByteArray())) }
    }

    /**
     * This device's round-1 state, recomputed rather than stored.
     *
     * [ChillDKG.ParticipantState1] is serializable, so this could be persisted —
     * but it is a pure function of the host key, the participant set and
     * [DkgSession.round1Random], all of which are already stored, so recomputing
     * keeps the ritual restart-safe without adding protocol state to the schema.
     */
    private fun participantState1(
        hostSeckey: PrivateKey,
        hostPublicKeys: List<PublicKey>,
        session: DkgSession
    ) = ChillDKG.participantStep1(
        hostSecretKey = hostSeckey,
        hostPublicKeys = hostPublicKeys,
        threshold = session.threshold,
        random = ByteVector32(session.round1Random)
    ).state

    /**
     * The coordinator's round-1 aggregation. Called twice — once to publish
     * `cmsg1`, once to rebuild the state the finalization needs — so the fault
     * check lives here rather than at both call sites.
     */
    private fun coordinatorStep1(
        pmsgs1: List<ByteArray>,
        hostPublicKeys: List<PublicKey>,
        session: DkgSession
    ) = ChillDKG.coordinatorStep1(
        participantMessages = pmsgs1.map { ByteVector(it) },
        hostPublicKeys = hostPublicKeys,
        threshold = session.threshold
    ).also { it.fault.raiseIfFaulty("coordinator round 1") }

    /**
     * Turns a protocol fault into an exception so it joins [advance]'s single
     * failure path.
     *
     * ChillDKG reports faults as values, not exceptions, because a faulty
     * participant is a normal outcome of a DKG rather than a bug. This ritual has
     * exactly one response to every one of them — the key is unusable, so the
     * session dies and the group is told — so there is nothing to gain from
     * handling them individually. What is worth keeping is the culprit: on a
     * failed DKG, which participant to blame is the only actionable thing there
     * is, and it goes into the failure reason shown to the user.
     */
    private fun ChilldkgFault.raiseIfFaulty(step: String) {
        if (isOk) return

        val culprit = participantIndex?.let { " (participant $it)" } ?: ""
        val reason = when (code) {
            ChilldkgFault.FAULTY_COORDINATOR -> "the coordinator is faulty"
            ChilldkgFault.FAULTY_PARTICIPANT -> "a participant is faulty"
            ChilldkgFault.FAULTY_PARTICIPANT_OR_COORDINATOR -> "a participant or the coordinator is faulty"
            ChilldkgFault.UNKNOWN_FAULTY_PARTICIPANT_OR_COORDINATOR ->
                "an unidentified participant or the coordinator is faulty"
            ChilldkgFault.INVALID_INPUT -> "invalid input"
            else -> "unrecognised fault code $code"
        }
        throw IllegalStateException("ChillDKG $step failed: $reason$culprit")
    }

    /**
     * Round messages in participant order — i.e. ordered by the sender's host key,
     * matching the [hostPublicKeys] ordering. Null while any are missing: both
     * coordinator steps require exactly `n` entries.
     */
    private suspend fun orderedPayloads(
        database: MantraDatabase,
        session: DkgSession,
        kind: Kind
    ): List<ByteArray>? {
        val hostKeyByParticipant = database.dkgSessionDao()
            .getMessagesByKind(session.id, DkgRitualEvents.HOST_KEY)
            .associate { it.participantPublicKey to it.payload }

        val messages = database.dkgSessionDao().getMessagesByKind(session.id, kind)
        if (messages.size < session.participantCount) {
            logger.d("Ritual ${session.id}: ${messages.size}/${session.participantCount} of kind $kind")
            return null
        }

        // Pair each message with its sender's host key so it can be sorted into
        // participant order. A message from someone who never published a host key
        // cannot be placed, and a short list would be rejected by ChillDKG anyway.
        val orderable = messages.mapNotNull { message ->
            hostKeyByParticipant[message.participantPublicKey]?.let { hostKey -> hostKey to message.payload }
        }
        if (orderable.size < session.participantCount) {
            logger.d("Ritual ${session.id}: ${orderable.size}/${session.participantCount} of kind $kind are placeable")
            return null
        }

        return orderable
            .sortedBy { (hostKey, _) -> hostKey }
            .map { (_, payload) -> payload.hexToByteArray() }
    }

    private suspend fun fail(database: MantraDatabase, session: DkgSession, reason: String) {
        database.dkgSessionDao().upsert(
            session.copy(
                stage = DkgRitualStage.FAILED,
                failureReason = reason,
                updatedAt = Clock.System.now()
            )
        )
    }

    private suspend fun publishHostKey(
        database: MantraDatabase,
        localChatRoom: LocalChatRoom,
        session: DkgSession
    ) = publishOwn(database, localChatRoom, session, DkgRitualEvents.HOST_KEY, session.hostPublicKey)

    /**
     * Broadcasts one of this device's own protocol messages AND records it
     * locally. The local copy matters: the coordinator is a participant too, and
     * its own message has to be in the aggregation alongside everyone else's.
     */
    private suspend fun publishOwn(
        database: MantraDatabase,
        localChatRoom: LocalChatRoom,
        session: DkgSession,
        kind: Kind,
        content: String
    ) {
        database.dkgSessionDao().upsert(
            DkgParticipantMessage(
                sessionId = session.id,
                participantPublicKey = session.userPublicKey,
                kind = kind,
                payload = content
            )
        )
        broadcast(database, localChatRoom, session, kind, content)
    }

    /**
     * Queues a ritual message as a gift-wrap payload. `NotaryViewModel` picks it
     * up, seals a copy per participant and broadcasts — the same path chat
     * messages take, which is why this needs no transport of its own.
     */
    private suspend fun broadcast(
        database: MantraDatabase,
        localChatRoom: LocalChatRoom,
        session: DkgSession,
        kind: Kind,
        content: String,
        includeThreshold: Boolean = false
    ) {
        val receiverTags = localChatRoom.localParticipants
            .filter { it.participant.participantPublicKey != session.userPublicKey }
            .map { localParticipant ->
                PTag.assemble(
                    localParticipant.participant.participantPublicKey,
                    localParticipant.participant.relayHint?.let { NormalizedRelayUrl(it) }
                )
            }

        val tags = receiverTags.toTypedArray() + DkgRitualEvents.assembleTags(
            sessionId = session.id,
            threshold = if (includeThreshold) session.threshold else null
        )

        val createdAt = Clock.System.now().epochSeconds
        val giftWrapPayloadId = EventHasher.hashId(
            pubKey = session.userPublicKey,
            createdAt = createdAt,
            tags = tags,
            content = content,
            kind = kind
        )

        database.giftWrapPayloadDao().upsert(
            GiftWrapPayload(
                id = giftWrapPayloadId,
                kind = kind,
                tags = tags,
                createdAt = Instant.fromEpochSeconds(createdAt),
                content = content,
                chatRoomId = localChatRoom.chatRoom.id,
                publicKey = session.userPublicKey
            )
        )
    }

    /** The room's creator, which is the ritual's coordinator by construction. */
    fun coordinatorOf(localChatRoom: LocalChatRoom): HexKey = localChatRoom.chatRoom.userPublicKey
}
