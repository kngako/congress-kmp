package press.mantra.compose.managers

import press.mantra.compose.database.MantraDatabase
import press.mantra.compose.database.model.Participant
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.exceptions.MarmotMissingChatGroupException
import press.mantra.compose.exceptions.MarmotUnsupportedWireFormatException
import press.mantra.compose.extensions.exporterSecret
import press.mantra.compose.extensions.toHex
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.marmot.GroupEventResult
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.CommitOrdering
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEvent
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEventEncryption
import com.vitorpamplona.quartz.marmot.mls.codec.TlsReader
import com.vitorpamplona.quartz.marmot.mls.codec.TlsWriter
import com.vitorpamplona.quartz.marmot.mls.crypto.MlsCryptoProvider
import com.vitorpamplona.quartz.marmot.mls.framing.ContentType
import com.vitorpamplona.quartz.marmot.mls.framing.MlsMessage
import com.vitorpamplona.quartz.marmot.mls.framing.PrivateMessage
import com.vitorpamplona.quartz.marmot.mls.framing.PublicMessage
import com.vitorpamplona.quartz.marmot.mls.framing.WireFormat
import com.vitorpamplona.quartz.marmot.mls.group.DecryptedMessage
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroup
import com.vitorpamplona.quartz.marmot.mls.group.RetainedEpochSecrets
import com.vitorpamplona.quartz.marmot.mls.schedule.KeySchedule
import com.vitorpamplona.quartz.marmot.mls.schedule.SecretTree
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import kotlin.time.Clock

object MarmotInboundManager {
    private const val TAG = "MarmotInboundManager"

    private val logger = Logger.withTag(TAG)


    /**
     * Number of past epochs to retain for late-arriving message decryption.
     * Matches MDK's `DEFAULT_EPOCH_LOOKBACK` so a message encrypted under
     * the prior N epochs' exporter secrets can still be decrypted after a
     * Commit advances the group. Capped for forward-secrecy reasons.
     */
    const val EPOCH_RETENTION_WINDOW = 5


    /** Size of reuse_guard in PrivateMessage (RFC 9420 §6.3.1) */
    private const val REUSE_GUARD_LENGTH = 4

    private val retainedEpochs = mutableMapOf<HexKey, MutableList<RetainedEpochSecrets>>()

    private val commitTracker = CommitOrdering.EpochCommitTracker()

    suspend fun processGroupMembershipChanges(
        database: MantraDatabase,
        localChatRoom: LocalChatRoom,
        mlsGroup: MlsGroup,
    ) {
        logger.d("processGroupMembershipChanges")
        val adminPublicKeys = mlsGroup.currentMarmotData()?.adminPubkeys

        val commitedGroupMembers = Participant.fromMlsGroup(
            mlsGroup = mlsGroup,
            nostrGroupId = localChatRoom.chatRoom.id,
            relayHint = mlsGroup.currentMarmotData()?.relays?.firstOrNull()
        )
        val committedGroupMembersPublicKeys = commitedGroupMembers.map { it.participantPublicKey }
        val excludedParticipants = localChatRoom.localParticipants.filter { localParticipant ->
            committedGroupMembersPublicKeys.contains(localParticipant.participant.participantPublicKey).not()
        }.map { localParticipant ->
            localParticipant.participant.copy(
                deletedAt = Clock.System.now()
            )
        }
        logger.d("excludedParticipants: $excludedParticipants")
        val existingParticipantPublicKeys = localChatRoom.localParticipants.map { it.participant.participantPublicKey }
        val newParticipants = commitedGroupMembers.filter { committedGroupMember ->
            existingParticipantPublicKeys.contains(committedGroupMember.participantPublicKey)
        }.map { newParticipant ->
            newParticipant.copy(
                adminAt = if (adminPublicKeys?.contains(newParticipant.participantPublicKey) == null) {
                    Clock.System.now()
                } else {
                    null
                }
            )
        }
        logger.d("newParticipants: $newParticipants")
        val removedAdmins = localChatRoom.localParticipants.filter { localParticipant ->
            localParticipant.participant.adminAt != null && adminPublicKeys?.contains(localParticipant.participant.participantPublicKey) == true
        }.map { localParticipant ->
            localParticipant.participant.copy(
                adminAt = null
            )
        }
        logger.d("removedAdmins: $removedAdmins")

        val graduatedAdmins = localChatRoom.localParticipants.filter { localParticipant ->
            localParticipant.participant.adminAt == null && adminPublicKeys?.contains(localParticipant.participant.participantPublicKey) == true
        }.map { localParticipant ->
            localParticipant.participant.copy(
                adminAt = Clock.System.now()
            )
        }
        logger.d("graduatedAdmins: $graduatedAdmins")

        // Handle membership changes...
        database.participantDao().upsert(removedAdmins)
        database.participantDao().delete(excludedParticipants)
        database.participantDao().upsert(newParticipants)
        database.participantDao().upsert(graduatedAdmins)
    }

    suspend fun processGroupEvent(
        database: MantraDatabase,
        activeKeyPair: KeyPair,
        localChatRoom: LocalChatRoom,
        mlsGroup: MlsGroup,
        groupEvent: GroupEvent
    ): GroupEventResult? {
        groupEvent.groupId()?.let { groupId ->
            return try {
                val mlsBytes = tryDecryptOuterLayer(
                    mlsGroup,
                    groupEvent.encryptedContent()
                )

                if (mlsBytes == null) {
                    // Expected when this kind:445 was encrypted with an epoch
                    // key that predates our join (classical MLS forward
                    // secrecy), or when the sender's epoch has drifted. Not
                    // an error — callers should log at DEBUG.
                    GroupEventResult.UndecryptableOuterLayer(
                        localChatRoom.chatRoom.id,
                        retainedEpochCount = retainedExporterSecrets(localChatRoom.chatRoom.id).size,
                    )
                } else {
                    val mlsMessage = MlsMessage.decodeTls(TlsReader(mlsBytes))

                    when (mlsMessage.wireFormat) {
                        WireFormat.PRIVATE_MESSAGE -> {
                            processPrivateMessage(
                                database = database,
                                mlsGroup = mlsGroup,
                                mlsMessage = mlsMessage,
                                groupEvent = groupEvent
                            )
                        }
                        WireFormat.PUBLIC_MESSAGE -> {
                            processPublicMessage(
                                database = database,
                                mlsGroup = mlsGroup,
                                mlsMessage = mlsMessage,
                                groupEvent = groupEvent
                            )
                        }
                        else -> {
                            throw MarmotUnsupportedWireFormatException(
                                "Unexpected wire format: ${mlsMessage.wireFormat}"
                            )
                        }
                    }
                }

            } catch (e: Throwable) {
                GroupEventResult.Error(groupId, "Failed to process GroupEvent: ${e.message}")
            }
        }

        return null
    }


    suspend fun processPrivateMessage(
        database: MantraDatabase,
        mlsGroup: MlsGroup,
        mlsMessage: MlsMessage,
        groupEvent: GroupEvent
    ): GroupEventResult  {
        val groupId = mlsGroup.currentMarmotData()?.nostrGroupId ?: mlsGroup.groupId.toHex()

        // Peek at content type from the PrivateMessage header
        val privMsg = PrivateMessage.decodeTls(TlsReader(mlsMessage.payload))

        return when (privMsg.contentType) {
            ContentType.APPLICATION -> {
                // MLS decrypt to get the inner plaintext
                val decrypted = decryptMessageBytes(
                    database,
                    mlsGroup,
                    mlsMessage.toTlsBytes()
                )
                logger.d("decrypted: $decrypted")
                val innerJson = decrypted.content.decodeToString()

                // MIP-03: if the inner application payload is a Nostr event,
                // its `pubkey` field MUST equal the MLS sender's credential
                // identity. Reject any mismatch — otherwise a group member
                // could mint events claiming a different author. Non-event
                // payloads (raw bytes via buildGroupEventFromBytes) bypass
                // this check since there is no author field to verify.
                val innerEvent = Event.fromJsonOrNull(innerJson)
                logger.d("innerEvent: ${innerEvent?.toJson()}")

                if (innerEvent != null) {
                    val senderIdentity = mlsGroup.memberIdentityHex(decrypted.senderLeafIndex)
                    if (senderIdentity == null || innerEvent.pubKey != senderIdentity) {
                        return GroupEventResult.Error(
                            groupId,
                            "MIP-03: inner event pubkey (${innerEvent.pubKey}) does not match MLS sender identity ($senderIdentity)",
                        )
                    }
                }

                GroupEventResult.ApplicationMessage(
                    groupId = groupId,
                    innerEventJson = innerJson,
                    senderLeafIndex = decrypted.senderLeafIndex,
                    epoch = decrypted.epoch,
                )
            }

            ContentType.COMMIT -> {
                handleCommitEvent(
                    database,
                    mlsGroup,
                    groupEvent
                )
            }

            ContentType.PROPOSAL -> {
                GroupEventResult.Error(groupId, "Standalone proposals not yet supported")
            }
        }
    }

    private suspend fun decryptMessageBytes(
        database: MantraDatabase,
        mlsGroup: MlsGroup,
        messageBytes: ByteArray,
    ): DecryptedMessage {
        val nostrGroupId = mlsGroup.currentMarmotData()?.nostrGroupId ?: mlsGroup.groupId.toHex()

        // Try current epoch. If we hit an exception here we MUST surface
        // it — commits that throw mid-processCommit leave the in-memory
        // group half-mutated, and a retry via `group.decrypt(...)` will
        // just report a stale "epoch mismatch" from the partial advance,
        // hiding the real bug. Capture the original throwable, try
        // retained epochs as a fallback, and re-raise the captured one
        // if nothing decrypts.
        val retainedBefore = mlsGroup.retainedSecrets()
        val preEpoch = mlsGroup.epoch
        val currentFailure: Throwable? =
            try {
                val result = mlsGroup.decrypt(messageBytes)
                // PrivateMessage commits apply inline through
                // `MlsGroup.decrypt` → `processCommit`; the epoch advances
                // in memory but the CLI reopens a fresh Context on every
                // command, so we MUST persist here or reloaded state
                // silently reverts to the pre-commit extensions (including
                // admin list).
                if (result.contentType == ContentType.COMMIT && mlsGroup.epoch != preEpoch) {
                    pushRetainedEpoch(nostrGroupId, retainedBefore)
                    persistGroup(
                        database,
                        mlsGroup
                    )
                }
                return result
            } catch (t: Throwable) {
                t
            }

        val retained = retainedEpochs[nostrGroupId] ?: emptyList()
        for (epochSecrets in retained) {
            val result = tryDecryptWithRetainedEpoch(messageBytes, epochSecrets)
            if (result != null) return result
        }

        throw currentFailure ?: IllegalStateException("Decrypt failed without captured cause")
    }

    public suspend fun processPublicMessage(
        database: MantraDatabase,
        mlsGroup: MlsGroup,
        mlsMessage: MlsMessage,
        groupEvent: GroupEvent
    ): GroupEventResult {
        val groupId = mlsGroup.currentMarmotData()?.nostrGroupId ?: mlsGroup.groupId.toHex()

        val pubMsg = PublicMessage.decodeTls(TlsReader(mlsMessage.payload))

        return when (pubMsg.contentType) {
            ContentType.COMMIT -> {
                handleCommitEvent(
                    database,
                    mlsGroup,
                    groupEvent
                )
            }

            ContentType.PROPOSAL -> {
                // wn/openmls publishes SelfRemove as a standalone PublicMessage
                // proposal — admins fold it into their next commit. Stage it
                // locally so a subsequent commit's `ProposalRef` can resolve;
                // without this every other member silently dropped the
                // proposal and the admin's commit then failed with "Commit
                // references unknown proposal" (marmot-interop test 15).
                try {
                    mlsGroup.receivePublicMessageProposal(pubMsg)
                    GroupEventResult.ProposalStaged(groupId, pubMsg.sender.leafIndex)
                } catch (e: Exception) {
                    GroupEventResult.Error(
                        groupId,
                        "Failed to stage standalone proposal: ${e.message}",
                        e,
                    )
                }
            }

            ContentType.APPLICATION -> {
                GroupEventResult.Error(groupId, "Application messages should use PrivateMessage")
            }
        }
    }

    private suspend fun handleCommitEvent(
        database: MantraDatabase,
        mlsGroup: MlsGroup,
        groupEvent: GroupEvent
    ): GroupEventResult {
        logger.d("handleCommitEvent: ${groupEvent.id}")
        val nostrGroupId = mlsGroup.currentMarmotData()?.nostrGroupId ?: mlsGroup.groupId.toHex()

        val currentEpoch = mlsGroup.epoch
        commitTracker.addCommit(nostrGroupId, currentEpoch, groupEvent)

        // If this is the only commit for this epoch, apply immediately
        val pending = commitTracker.pendingForEpoch(nostrGroupId, currentEpoch)
        return if (pending.size == 1) {
            val result = applyCommit(
                database,
                mlsGroup,
                groupEvent
            )
            commitTracker.clearEpoch(nostrGroupId, currentEpoch)
            result
        } else {
            GroupEventResult.CommitPending(nostrGroupId, currentEpoch)
        }
    }

    private suspend fun applyCommit(
        database: MantraDatabase,
        mlsGroup: MlsGroup,
        commitEvent: GroupEvent,
    ): GroupEventResult =
        try {
            val groupId = mlsGroup.currentMarmotData()?.nostrGroupId ?: mlsGroup.groupId.toHex()

            val mlsBytes =
                tryDecryptOuterLayer(mlsGroup, commitEvent.encryptedContent())
                    ?: return GroupEventResult.UndecryptableOuterLayer(
                        groupId,
                        retainedEpochCount = retainedExporterSecrets(groupId).size,
                    )
            val mlsMessage = MlsMessage.decodeTls(TlsReader(mlsBytes))

            when (mlsMessage.wireFormat) {
                WireFormat.PRIVATE_MESSAGE -> {
                    // Sniff the PrivateMessage epoch without consuming any
                    // ratchet state. Past-epoch echoes and future-epoch
                    // arrivals must not advance the secret tree — otherwise
                    // the real handshake / application message gets rejected
                    // when it finally arrives.
                    val privPeek = PrivateMessage.decodeTls(TlsReader(mlsMessage.payload))
                    val currentEpoch = mlsGroup.epoch
                    when {
                        privPeek.epoch < currentEpoch -> {
                            GroupEventResult.Duplicate(groupId)
                        }

                        privPeek.epoch > currentEpoch -> {
                            GroupEventResult.Error(
                                groupId,
                                "PrivateMessage epoch ${privPeek.epoch} is ahead of local epoch $currentEpoch; ignoring",
                            )
                        }

                        else -> {
                            val decrypted = mlsGroup.decrypt(mlsMessage.toTlsBytes())
                            if (decrypted.contentType == ContentType.COMMIT) {
                                GroupEventResult.CommitProcessed(groupId, mlsGroup.epoch ?: 0)
                            } else {
                                GroupEventResult.Error(
                                    groupId,
                                    "Expected COMMIT but got ${decrypted.contentType}",
                                )
                            }
                        }
                    }
                }

                WireFormat.PUBLIC_MESSAGE -> {
                    val pubMsg = PublicMessage.decodeTls(TlsReader(mlsMessage.payload))
                    val tag = pubMsg.confirmationTag
                    val currentEpoch = mlsGroup.epoch
                    when {
                        tag == null -> {
                            GroupEventResult.Error(groupId, "PublicMessage commit missing confirmation_tag")
                        }

                        // Reject commits that are not for our current epoch.
                        // Happens most commonly when our own already-applied
                        // commit is echoed back from the relay after an app
                        // restart (the in-memory dedup set is cleared), and
                        // the outer layer decrypts via a retained epoch key.
                        // Calling `processCommit` on a past-epoch commit
                        // partially mutates tree / groupContext / epochSecrets
                        // before throwing on the confirmation-tag check,
                        // leaving the local state diverged from every other
                        // member's — they then can't decrypt anything we
                        // send next.
                        pubMsg.epoch < currentEpoch -> {
                            GroupEventResult.Duplicate(groupId)
                        }
                        pubMsg.epoch > currentEpoch -> {
                            GroupEventResult.Error(
                                groupId,
                                "Commit epoch ${pubMsg.epoch} is ahead of local epoch $currentEpoch; ignoring",
                            )
                        }

                        else -> {
                            // RFC 9420 §6.2 — reject PublicMessage commits
                            // whose membership_tag doesn't match what the
                            // current epoch's membership_key would produce.
                            // Without this an outsider with the outer
                            // exporter secret could inject arbitrary commit
                            // bytes and advance the group past them.
                            if (!mlsGroup.verifyPublicMessageCommitMembershipTag(pubMsg)) {
                                GroupEventResult.Error(
                                    groupId,
                                    "Invalid membership_tag on PublicMessage commit",
                                )
                            } else {
                                processCommit(
                                    database,
                                    mlsGroup,
                                    commitBytes = pubMsg.content,
                                    senderLeafIndex = pubMsg.sender.leafIndex,
                                    confirmationTag = tag,
                                    signature = pubMsg.signature,
                                )
                                GroupEventResult.CommitProcessed(groupId, mlsGroup.epoch)
                            }
                        }
                    }
                }

                else -> {
                    GroupEventResult.Error(groupId, "Unexpected wire format for commit")
                }
            }
        } catch (e: Exception) {
            GroupEventResult.Error(
                groupId = mlsGroup.currentMarmotData()?.nostrGroupId ?: mlsGroup.groupId.toHex(),
                "Failed to apply commit: ${e.message}",
                e
            )
        }

    /**
     * Process a received Commit, advancing the epoch.
     *
     * @param mlsGroup MLS Group
     * @param commitBytes TLS-serialized Commit
     * @param senderLeafIndex sender's leaf index
     * @param confirmationTag optional confirmation tag for verification
     */
    suspend fun processCommit(
        database: MantraDatabase,
        mlsGroup: MlsGroup,
        commitBytes: ByteArray,
        senderLeafIndex: Int,
        confirmationTag: ByteArray,
        signature: ByteArray = ByteArray(0),
        wireFormat: WireFormat = WireFormat.PUBLIC_MESSAGE,
    ) {

        // Capture the outgoing epoch's secrets BEFORE advancing, but only
        // commit them to the retention window once processCommit succeeds —
        // otherwise a failed commit (e.g. "Duplicate encryption key" on an
        // add-me relay echo) would pollute the window with a duplicate of
        // the current epoch key, wasting the finite retention slots.
        val retainedBefore = mlsGroup.retainedSecrets()

        mlsGroup.processCommit(commitBytes, senderLeafIndex, confirmationTag, signature, wireFormat)

        pushRetainedEpoch(
            nostrGroupId = mlsGroup.currentMarmotData()?.nostrGroupId ?: mlsGroup.groupId.toHex(),
            retainedBefore
        )
        persistGroup(
            database,
            mlsGroup
        )
    }

    /**
     * Decrypt the outer ChaCha20-Poly1305 layer, trying the current epoch's
     * exporter key first and falling back to retained epoch exporter keys.
     *
     * After a commit advances the epoch, late-arriving messages encrypted
     * with the previous epoch's exporter key would fail without this fallback.
     *
     * Returns null when neither the current epoch key nor any retained key
     * decrypts. This happens normally for commits/application messages from
     * epochs that predate our join (we never held those keys), so callers
     * should treat null as an expected "nothing to do here" outcome and log
     * at DEBUG, not as an error.
     */
    private fun tryDecryptOuterLayer(
        mlsGroup: MlsGroup,
        encryptedContent: String,
    ): ByteArray? {
        // Try current epoch key first
        try {
            val exporterKey = mlsGroup.exporterSecret()
            logger.d("exporterKey: ${exporterKey.toHex()}")
            return GroupEventEncryption.decrypt(encryptedContent, exporterKey)
        } catch (_: Exception) {
            // Current epoch key failed — try retained epoch keys
        }

        // Try retained epoch exporter keys (most recent first)
        logger.d("Try retained")
        val retainedKeys = retainedExporterSecrets(
            nostrGroupId = mlsGroup.currentMarmotData()?.nostrGroupId ?: mlsGroup.groupId.toHex()
        )
        for (retainedKey in retainedKeys) {
            try {
                return GroupEventEncryption.decrypt(encryptedContent, retainedKey)
            } catch (_: Exception) {
                // This retained key didn't work — try the next one
            }
        }

        return null
    }

    private suspend fun persistGroup(
        database: MantraDatabase,
        mlsGroup: MlsGroup
    ) {
        val nostrGroupId = mlsGroup.currentMarmotData()?.nostrGroupId ?: mlsGroup.groupId.toHex()

        try {
            val state = mlsGroup.saveState()
            val encoded = state.encodeTls()
            logger.d { "persistGroup($nostrGroupId): serialized ${encoded.size} bytes, calling store.save" }

            val localChatRoom = database.chatRoomDao().findChatRoomById(nostrGroupId)

            if (localChatRoom == null) {
                throw MarmotMissingChatGroupException("chatRoom not found in database, skipping")
            } else {
                database.chatRoomDao().upsert(
                    localChatRoom.chatRoom.copy(
                        mlsGroupState = encoded.toHex()
                    )
                )
            }

            // Also persist retained epochs
            val retained = retainedEpochs[nostrGroupId]
            if (retained != null) {
                val retainedBytes =
                    retained.map { epoch ->
                        val writer = TlsWriter()
                        epoch.encodeTls(writer)
                        writer.toByteArray()
                    }
//              TODO:  store.saveRetainedEpochs(nostrGroupId, retainedBytes)
                logger.d { "persistGroup($nostrGroupId): persisted ${retainedBytes.size} retained epochs" }
            }
        } catch (e: Exception) {
            logger.e("persistGroup($nostrGroupId) FAILED: ${e.message}", e)
            throw e
        }
    }

    /**
     * Push a previously-captured [RetainedEpochSecrets] into the bounded
     * retention window. Call after the epoch advance has been applied
     * successfully so that failed commits don't pollute the window with
     * duplicate current-epoch keys.
     */
    private fun pushRetainedEpoch(
        nostrGroupId: HexKey,
        retainedBefore: RetainedEpochSecrets,
    ) {
        val retained = retainedEpochs.getOrPut(nostrGroupId) { mutableListOf() }
        retained.add(retainedBefore)

        // Trim to retention window (keep only the most recent N-1 epochs)
        while (retained.size > EPOCH_RETENTION_WINDOW) {
            retained.removeAt(0)
        }
    }

    private fun tryDecryptWithRetainedEpoch(
        messageBytes: ByteArray,
        retained: RetainedEpochSecrets,
    ): DecryptedMessage? =
        try {
            val secretTree = SecretTree(retained.encryptionSecret, retained.leafCount)
            val mlsMsg =
                MlsMessage
                    .decodeTls(TlsReader(messageBytes))

            if (mlsMsg.wireFormat != WireFormat.PRIVATE_MESSAGE) {
                return null
            }

            val privMsg = PrivateMessage.decodeTls(
                TlsReader(
                    mlsMsg.payload
                )
            )
            if (privMsg.epoch != retained.epoch) return null

            // Derive sender data key/nonce using ciphertext sample (RFC 9420 §6.3.1)
            // RFC 9420 §6.3.2: ciphertext_sample is the first KDF.Nh bytes
            // (32 for HKDF-SHA256), not AEAD.Nk (16). Using AEAD.Nk here made
            // sender-data decryption silently fail for every retained-epoch
            // message and turned the fallback path into a no-op — the
            // symptom was interop Test 12 (offline catch-up): kind:9
            // messages encrypted under epoch N arriving after a 1→N+1
            // commit got rejected with "Message epoch X doesn't match
            // current epoch Y" instead of being pulled through this path.
            // Same fix already applied to MlsGroup.decrypt (line ~878);
            // this branch was missed when that one was patched.
            val ciphertextSample =
                privMsg.ciphertext.copyOfRange(0, minOf(privMsg.ciphertext.size, MlsCryptoProvider.HASH_OUTPUT_LENGTH))
            val senderDataKey =
                MlsCryptoProvider.expandWithLabel(
                    retained.senderDataSecret,
                    "key",
                    ciphertextSample,
                    MlsCryptoProvider.AEAD_KEY_LENGTH,
                )
            val senderDataNonce =
                MlsCryptoProvider.expandWithLabel(
                    retained.senderDataSecret,
                    "nonce",
                    ciphertextSample,
                    MlsCryptoProvider.AEAD_NONCE_LENGTH,
                )

            // Build SenderDataAAD
            val senderDataAad = TlsWriter()
            senderDataAad.putOpaqueVarInt(privMsg.groupId)
            senderDataAad.putUint64(privMsg.epoch)
            senderDataAad.putUint8(privMsg.contentType.value)

            val senderDataPlain =
                MlsCryptoProvider.aeadDecrypt(
                    senderDataKey,
                    senderDataNonce,
                    senderDataAad.toByteArray(),
                    privMsg.encryptedSenderData,
                )
            val senderReader = TlsReader(senderDataPlain)
            val senderLeafIndex = senderReader.readUint32().toInt()
            val generation = senderReader.readUint32().toInt()
            val reuseGuard = senderReader.readBytes(REUSE_GUARD_LENGTH)

            val kng = secretTree.applicationKeyNonceForGeneration(senderLeafIndex, generation)

            // Apply reuse_guard XOR to nonce
            val guardedNonce = kng.nonce.copyOf()
            for (i in 0 until REUSE_GUARD_LENGTH) {
                guardedNonce[i] = (guardedNonce[i].toInt() xor reuseGuard[i].toInt()).toByte()
            }

            // Build PrivateContentAAD
            val contentAad = TlsWriter()
            contentAad.putOpaqueVarInt(privMsg.groupId)
            contentAad.putUint64(privMsg.epoch)
            contentAad.putUint8(privMsg.contentType.value)
            contentAad.putOpaqueVarInt(privMsg.authenticatedData)

            val pmcBytes =
                MlsCryptoProvider.aeadDecrypt(kng.key, guardedNonce, contentAad.toByteArray(), privMsg.ciphertext)

            // AEAD plaintext is a PrivateMessageContent struct (RFC 9420
            // §6.3.1): `applicationData<V> || signature<V> || padding`. The
            // main decrypt path parses this and returns the inner
            // applicationData; the retained-epoch branch was returning the
            // raw struct, which made callers see a length-prefixed blob
            // with signature + zero-padding glued onto the end. Extract the
            // applicationData the same way.
            val pmcReader = TlsReader(pmcBytes)
            val applicationData = pmcReader.readOpaqueVarInt()

            DecryptedMessage(
                senderLeafIndex = senderLeafIndex,
                contentType = privMsg.contentType,
                content = applicationData,
                epoch = privMsg.epoch,
            )
        } catch (_: Exception) {
            null
        }


    /**
     * Return exporter secrets from retained epochs for a group.
     *
     * Used by the inbound processor to attempt outer decryption with
     * previous epoch keys when the current epoch's key fails (e.g.,
     * after a commit has advanced the epoch but late-arriving messages
     * still use the old exporter key).
     *
     * @param nostrGroupId hex-encoded Nostr group ID
     * @return list of retained exporter secrets (most recent first), each
     *         derived via MLS-Exporter("marmot", "group-event", 32)
     */
    fun retainedExporterSecrets(nostrGroupId: HexKey): List<ByteArray> {
        val retained = retainedEpochs[nostrGroupId] ?: return emptyList()
        return retained
            .filter { it.exporterSecret.isNotEmpty() }
            .sortedByDescending { it.epoch }
            .map { epochSecrets ->
                KeySchedule.mlsExporter(
                    epochSecrets.exporterSecret,
                    "marmot",
                    "group-event".encodeToByteArray(),
                    32,
                )
            }
    }

}