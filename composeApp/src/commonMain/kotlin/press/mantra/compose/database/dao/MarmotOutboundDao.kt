package press.mantra.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Transaction
import press.mantra.compose.database.MantraDatabase
import press.mantra.compose.database.model.BroadcastNostrEventRequest
import press.mantra.compose.database.model.ChatMessage
import press.mantra.compose.database.model.ChatMessageBroadcastNostrEventRequestRelation
import press.mantra.compose.database.model.ChatMessageNostrEventRelation
import press.mantra.compose.database.model.ChatRoom
import press.mantra.compose.database.model.GiftWrapPayload
import press.mantra.compose.database.model.MarmotCommitResult
import press.mantra.compose.database.model.MarmotGroupEvent
import press.mantra.compose.database.model.MarmotInnerEvent
import press.mantra.compose.database.model.MarmotInnerEvent.Companion.disappearingExpiration
import press.mantra.compose.database.model.MarmotKeyPackage
import press.mantra.compose.database.model.MarmotRetainedEpochSecret
import press.mantra.compose.database.model.NostrEvent
import press.mantra.compose.database.model.Participant
import press.mantra.compose.exceptions.MarmotMissingChatGroupException
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import press.mantra.compose.extensions.exporterSecret
import press.mantra.compose.extensions.toHex
import press.mantra.compose.managers.MarmotInboundManager.EPOCH_RETENTION_WINDOW
import press.mantra.compose.nostr.Relays
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.tags.EncodingTag
import com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupData
import com.vitorpamplona.quartz.marmot.mip02Welcome.WelcomeEvent
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEvent
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEventEncryption
import com.vitorpamplona.quartz.marmot.mls.codec.TlsReader
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroup
import com.vitorpamplona.quartz.marmot.mls.messages.MlsKeyPackage
import com.vitorpamplona.quartz.marmot.mls.tree.Credential
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler
import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Instant

@Dao
abstract class MarmotOutboundDao(
    private val database: MantraDatabase
) {
    val logger = Logger.withTag(TAG)

    @Transaction
    open suspend fun createMlsDirectMessageChatRoom(
        name: String? = null,
        description: String? = null,
        userPublicKey: HexKey,
        peerPublicKey: HexKey,
        peerKeyPackage: MarmotKeyPackage,
        relays: List<String> = Relays.DefaultDMRelayList.map { it.url }
    ): String {
        val nostrGroupId = RandomInstance.bytes(32).toHexKey()
        val mlsGroup = MlsGroup.create(
            identity = userPublicKey.hexToByteArray(),
            initialExtensions = listOf(
                MarmotGroupData.bootstrap(
                    nostrGroupId = nostrGroupId,
                    creatorPubKey = userPublicKey,
                    outboxRelays = relays,
                ).toExtension()
            )
        )

        // Save Chat Room
        val chatRoom = ChatRoom(
            id = nostrGroupId,
            userPublicKey = userPublicKey,
            mlsGroupState = mlsGroup.saveState().encodeTls().toHex(),
            subject = name,
            description = description
        )
        database.chatRoomDao().upsert(
            chatRoom
        )

        // Save Participants...
        database.participantDao().upsert(
            listOf(
                Participant(
                    participantPublicKey = userPublicKey,
                    chatRoomId = chatRoom.id,
                    relayHint = relays.first()
                ),
                Participant(
                    participantPublicKey = peerPublicKey,
                    chatRoomId = chatRoom.id,
                    relayHint = relays.first()
                )
            )
        )

        // Invite peer...
        inviteMember(
            nostrGroupId = nostrGroupId,
            mlsGroup = mlsGroup,
            userPublicKey = userPublicKey,
            peerPublicKey = peerPublicKey,
            peerKeyPackage = peerKeyPackage,
            relays = relays
        )

        // Update chatRoom
        database.chatRoomDao().upsert(
            chatRoom.copy(
                mlsGroupState = mlsGroup.saveState().encodeTls().toHex(),
            )
        )

        return nostrGroupId
    }

    @Transaction
    open suspend fun inviteMemberToChatRoom(
        localChatRoom: LocalChatRoom,
        peerPublicKey: HexKey,
        peerKeyPackage: MarmotKeyPackage,
    ) {
        // A room restored from an inbound gift wrap has no persisted MLS state, so there is
        // nothing to add a member to. Say so instead of silently doing nothing and letting the
        // caller report success.
        val mlsGroup = localChatRoom.chatRoom.toMlsGroup()
            ?: throw MarmotMissingChatGroupException(
                "No MLS group state for chat room ${localChatRoom.chatRoom.id}; cannot invite $peerPublicKey"
            )

        val relays = Relays.DefaultDMRelayList.map { it.url }

        // The invitee needs a Participant row before the Welcome is sealed: sealGiftWrapPayload
        // walks the participants of the room to decide who to wrap for, so without this the
        // Welcome produced no gift wraps at all and sat unsealed forever.
        database.participantDao().upsert(
            listOf(
                Participant(
                    participantPublicKey = peerPublicKey,
                    chatRoomId = localChatRoom.chatRoom.id,
                    relayHint = relays.first()
                )
            )
        )

        inviteMember(
            nostrGroupId = localChatRoom.chatRoom.id,
            mlsGroup = mlsGroup,
            userPublicKey = localChatRoom.chatRoom.userPublicKey,
            peerPublicKey = peerPublicKey,
            peerKeyPackage = peerKeyPackage,
            relays = relays
        )

        // `addMember` advanced the in-memory group to the next epoch. Without persisting it the
        // creator kept encrypting under the old epoch — which the new member cannot decrypt —
        // and the next invite would re-derive from stale state and produce a conflicting commit.
        database.chatRoomDao().upsert(
            localChatRoom.chatRoom.copy(
                mlsGroupState = mlsGroup.saveState().encodeTls().toHex()
            )
        )
    }

    private suspend fun inviteMember(
        nostrGroupId: HexKey,
        mlsGroup: MlsGroup,
        userPublicKey: HexKey,
        peerPublicKey: HexKey,
        peerKeyPackage: MarmotKeyPackage,
        relays: List<String> = Relays.DefaultDMRelayList.map { it.url },
    ) {
        logger.d("inviteMember: $peerPublicKey")

        // Read before `addMember` advances the tree. A group holding only its
        // creator has nobody to tell about the commit -- no other member exists, and
        // nobody outside the group can decrypt it -- so broadcasting one is noise,
        // and deferring the welcome until a relay acknowledges that noise is how the
        // first invitee ends up never hearing anything at all.
        //
        // Derived here rather than passed in because no caller is in a better
        // position to know it, and both of the ones that tried got it wrong:
        // inviteMemberToChatRoom hardcoded false, so every group's first invite took
        // the deferred path. `members()` skips empty leaves, so this stays right for
        // a group that has had members removed.
        //
        // True exactly once per group, whatever its final size: see
        // docs/marmot-membership.md.
        val isOneMemberInitialGroupCreation = mlsGroup.members().size == 1
        val keyPackage = MlsKeyPackage.decodeTls(
            TlsReader(
                peerKeyPackage.tlsEncodedMarmotKeyPackage
            )
        )

        val credential = keyPackage.leafNode.credential
        require(credential is Credential.Basic) {
            "KeyPackage must use BasicCredential"
        }
        require(credential.identity.toHexKey() == peerPublicKey) {
            "KeyPackage credential identity does not match memberPubKey"
        }

        val retainedBefore = mlsGroup.retainedSecrets()
        val commitResult = mlsGroup.addMember(
            peerKeyPackage.tlsEncodedMarmotKeyPackage
        )

        database.marmotRetainedEpochSecretDao().insert(
            MarmotRetainedEpochSecret(
                chatRoomId = nostrGroupId,
                epoch = retainedBefore.epoch,
                senderDataSecret = retainedBefore.senderDataSecret,
                encryptionSecret = retainedBefore.encryptionSecret,
                leafCount = retainedBefore.leafCount,
                exporterSecret = retainedBefore.exporterSecret,
            )
        )

        // Trim retained epochs out of the retention window...
        val defenestratableMarmotRetainedEpochSecrets = database.marmotRetainedEpochSecretDao()
            .getDefenestratableMarmotRetainedEpochSecretForChatRoomId(
                chatRoomId = nostrGroupId,
                epochCutOffPoint = retainedBefore.epoch - EPOCH_RETENTION_WINDOW.toLong()
            )
        logger.d("Defenestratable Retained Epochs: ${defenestratableMarmotRetainedEpochSecrets.map { it.epoch }}")
        database.marmotRetainedEpochSecretDao().defenestrate(
            defenestratableMarmotRetainedEpochSecrets
        )

        if (isOneMemberInitialGroupCreation) {
            // Send welcome event to participant
            commitResult.welcomeBytes?.let { welcomeBytes ->
                deliveryWelcome(
                    nostrGroupId = nostrGroupId,
                    userPublicKey = userPublicKey,
                    welcomeBytes = welcomeBytes,
                    peerKeyPackageEventId = peerKeyPackage.id,
                    relays = relays,
                    createdAt = Clock.System.now()
                )
            }
        } else {
            // CommitEvent...
            val encryptedContent = encryptedCommitEvent(
                mlsGroup,
                commitBytes = commitResult.framedCommitBytes,
                exporterKey = commitResult.preCommitExporterSecret
            )

            val template = GroupEvent.build(
                encryptedContentBase64 = encryptedContent,
                nostrGroupId = nostrGroupId
            )

            val ephemeralSigner = NostrSignerInternal(KeyPair())
            val commitEvent: GroupEvent = ephemeralSigner.sign(template)

            database.nostrEventDao().upsert(
                NostrEvent(
                    id = commitEvent.id,
                    createdAt = Instant.fromEpochSeconds(commitEvent.createdAt),
                    content = commitEvent.content,
                    sig = commitEvent.sig,
                    tags = commitEvent.tags,
                    kind = commitEvent.kind,
                    pubKey = commitEvent.pubKey
                )
            )

            database.marmotGroupEventDao().upsert(
                MarmotGroupEvent(
                    id = commitEvent.id,
                    createdAt = Instant.fromEpochSeconds(commitEvent.createdAt),
                    encryptedContent = commitEvent.encryptedContent(),
                    publicKey = commitEvent.pubKey,
                    userPublicKey = userPublicKey,
                    expiresAt = commitEvent.expiration()?.let { Instant.fromEpochSeconds(it) },
                    chatRoomId = nostrGroupId,
                    signature = commitEvent.sig
                )
            )

            // Save commitResult... in case we need to broadcast welcomeEvent after relay acknowledgement...
            database.marmotCommitResultDao().upsert(
                MarmotCommitResult(
                    id = commitEvent.id,
                    isOneMemberInitialGroupCreation = isOneMemberInitialGroupCreation,
                    chatRoomId = nostrGroupId,
                    commitBytes = commitResult.commitBytes,
                    preCommitExporterSecret = commitResult.preCommitExporterSecret,
                    welcomeBytes = commitResult.welcomeBytes,
                    framedCommitBytes = commitResult.preCommitExporterSecret,
                    groupInfoBytes = commitResult.groupInfoBytes,
                    userPublicKey = userPublicKey,
                    peerKeyPackageEventId = peerKeyPackage.id,
                    createdAt = Instant.fromEpochSeconds(commitEvent.createdAt)
                )
            )

            // Broadcast commitment so other members get it...
            database.broadcastNostrEventRequestDao().insert(
                relays.map {
                    BroadcastNostrEventRequest(
                        nostrEventId = commitEvent.id,
                        relayURL = it
                    )
                }
            )
        }
    }

    suspend fun deliveryWelcome(
        nostrGroupId: HexKey,
        userPublicKey: HexKey,
        welcomeBytes: ByteArray,
        peerKeyPackageEventId: HexKey,
        relays: List<String>,
        createdAt: Instant
    ) {
        try {
            val welcomeBase64 = Base64.encode(
                source = welcomeBytes
            )

            // GiftWrap...
            val welcomeEvent = WelcomeEvent.build(
                welcomeBase64 = welcomeBase64,
                createdAt = createdAt.epochSeconds,
                keyPackageEventId = peerKeyPackageEventId,
                relays = relays.map { NormalizedRelayUrl(it) },
                nostrGroupId = nostrGroupId,
            )
            // Compute welcomeEventId
            val welcomeEventId = EventHasher.hashId(
                pubKey = userPublicKey,
                kind = welcomeEvent.kind,
                tags = welcomeEvent.tags,
                createdAt = welcomeEvent.createdAt,
                content = welcomeEvent.content
            )
            logger.d("Welcome Event: ${welcomeEvent.toJson()}")
            logger.d("Welcome Event Id: $welcomeEventId")


            database.giftWrapPayloadDao().insert(
                GiftWrapPayload(
                    id = welcomeEventId,
                    publicKey = userPublicKey,
                    chatRoomId = nostrGroupId,
                    kind = welcomeEvent.kind,
                    tags = welcomeEvent.tags,
                    content = welcomeEvent.content,
                    createdAt = Instant.fromEpochSeconds(welcomeEvent.createdAt)
                )
            )

            database.marmotKeyPackageDao().getMarmotKeyPackageById(peerKeyPackageEventId)?.let { marmotKeyPackage ->
                database.profileDao().getProfileByPublicKey(marmotKeyPackage.publicKey)?.let { profile ->
                    // Save chatMessage for the invite...
                    database.chatMessageDao().upsert(
                        ChatMessage(
                            content = "Invited ${profile.humanReadableNameOrPubkey() ?: "participant"} to chat", // use profile.humanReadable...
                            chatRoomId = nostrGroupId,
                            senderPublicKey = userPublicKey,
                            isUserMessage = true, // TODO: This is information message...
                            giftWrapPayloadId = welcomeEventId,
                            marmotGroupEventId = null,
                            marmotInnerEventId = null
                        )
                    )
                }
            }
        } catch (e: Throwable) {
            logger.e("Failed to deliver welcome:", e)
        }
    }

    private fun encryptedCommitEvent(
        mlsGroup: MlsGroup,
        commitBytes: ByteArray,
        exporterKey: ByteArray? = null,
    ): String {
        val outerKey = exporterKey ?: mlsGroup.exporterSecret()

        return GroupEventEncryption.encrypt(commitBytes, outerKey)
    }

    @Transaction
    open suspend fun encryptAndSendMarmotInnerEvent(
        localChatRoom: LocalChatRoom,
        mlsGroup: MlsGroup,
        marmotInnerEvent: MarmotInnerEvent,
        nostrSignerSync: NostrSignerSync
    ): GroupEvent {
        val innerEvent = RumorAssembler.assembleRumor(
            pubKey = nostrSignerSync.pubKey,
            ev = EventTemplate(
                createdAt = marmotInnerEvent.createdAt.epochSeconds,
                content = marmotInnerEvent.content,
                tags = marmotInnerEvent.tags,
                kind = marmotInnerEvent.kind
            )
        )

        logger.d("InnerEvent: ${innerEvent.toJson()}")

        val plainTextBytes = innerEvent.toJson().encodeToByteArray()
        logger.d("plainTextBytes: ${plainTextBytes.toHex()}")

        // Step 1: MLS encrypt
        val mlsCipherText = mlsGroup.encrypt(plainTextBytes)
        logger.d("mlsCipherText: ${mlsCipherText.toHex()}")

        // Step 2: Outer ChaCha20-Poly1305 encryption
        val exportKey = mlsGroup.exporterSecret()
        logger.d("exporterKey: ${exportKey.toHex()}")
        val encryptedContent = GroupEventEncryption.encrypt(
            mlsMessageBytes = mlsCipherText,
            groupKey = exportKey
        )
        logger.d("encryptedContent: $encryptedContent")

        // Step 3: Build the GroupEvent template (auto-apply NIP-40 expiration
        // if the group has disappearing_message_secs configured per MIP-01/03).
        val createdAt = TimeUtils.now()
        val expirationTime = disappearingExpiration(mlsGroup, createdAt)

        val template = GroupEvent.build(
            encryptedContentBase64 = encryptedContent,
            nostrGroupId = localChatRoom.chatRoom.id,
            createdAt = createdAt,
        ) {
            if (expirationTime != null) expiration(expirationTime)
            addUnique(
                EncodingTag.assemble()
            )
        }

        // Step 4: Sign with a fresh ephemeral keypair
        val ephemeralSigner = NostrSignerInternal(KeyPair())
        val groupEvent: GroupEvent = ephemeralSigner.sign(template)
        logger.d("groupEvent: ${groupEvent.toJson()}")

        // May need to update chatRoom mlsGroupState... or last sent message
        database.chatRoomDao().upsert(
            localChatRoom.chatRoom.copy(
                mlsGroupState = mlsGroup.saveState().encodeTls().toHex()
            )
        )

        database.nostrEventDao().upsert(
            NostrEvent(
                id = groupEvent.id,
                tags = groupEvent.tags,
                createdAt = Instant.fromEpochSeconds(groupEvent.createdAt),
                content = groupEvent.content,
                sig = groupEvent.sig,
                kind = groupEvent.kind,
                pubKey = groupEvent.pubKey
            )
        )

        database.marmotGroupEventDao().upsert(
            MarmotGroupEvent(
                id = groupEvent.id,
                createdAt = Instant.fromEpochSeconds(groupEvent.createdAt),
                expiresAt = groupEvent.expiration()?.let { Instant.fromEpochSeconds(it) },
                encryptedContent = groupEvent.encryptedContent(),
                chatRoomId = localChatRoom.chatRoom.id,
                userPublicKey = groupEvent.pubKey,
                publicKey = groupEvent.pubKey,
                signature = groupEvent.sig,
            )
        )

        database.marmotInnerEventDao().upsert(
            marmotInnerEvent.copy(
                marmotGroupEventId = groupEvent.id
            )
        )

        val chatMessageOrNull = database.chatMessageDao().getChatMessagesByMarmotInnerEventId(innerEvent.id)

        logger.d("chatMessageOrNull: $chatMessageOrNull")
        chatMessageOrNull?.let { chatMessage ->
            database.chatMessageNostrEventRelationDao().upsert(
                ChatMessageNostrEventRelation(
                    chatMessageId = chatMessage.id,
                    nostrEventId = groupEvent.id
                )
            )

            database.chatMessageDao().upsert(
                chatMessage.copy(
                    marmotGroupEventId = groupEvent.id
                )
            )

            // Sync broadcast to all the required relays...
            val broadcastNostrEventRequestIds = database.broadcastNostrEventRequestDao().insert(
                Relays.DefaultDMRelayList.map { // TODO: Get these from localChatRoom...
                    BroadcastNostrEventRequest(
                        nostrEventId = groupEvent.id,
                        relayURL = it.url
                    )
                }
            )

            broadcastNostrEventRequestIds.forEach { broadcastNostrEventRequestId ->
                database.chatMessageBroadcastNostrEventRequestRelationDao().upsert(
                    ChatMessageBroadcastNostrEventRequestRelation(
                        chatMessageId = chatMessage.id,
                        broadcastNostrEventRequestId = broadcastNostrEventRequestId
                    )
                )
            }
        }

        return groupEvent
    }

    companion object {
        const val TAG = "MarmotOutboundDao"
    }
}