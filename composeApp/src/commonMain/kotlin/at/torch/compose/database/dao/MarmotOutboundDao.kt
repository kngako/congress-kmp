package at.torch.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Transaction
import at.torch.compose.database.TorchDatabase
import at.torch.compose.database.model.BroadcastNostrEventRequest
import at.torch.compose.database.model.ChatMessage
import at.torch.compose.database.model.ChatMessageBroadcastNostrEventRequestRelation
import at.torch.compose.database.model.ChatMessageNostrEventRelation
import at.torch.compose.database.model.ChatRoom
import at.torch.compose.database.model.GiftWrapPayload
import at.torch.compose.database.model.MarmotGroupEvent
import at.torch.compose.database.model.MarmotInnerEvent
import at.torch.compose.database.model.MarmotInnerEvent.Companion.disappearingExpiration
import at.torch.compose.database.model.MarmotKeyPackage
import at.torch.compose.database.model.NostrEvent
import at.torch.compose.database.model.intermdiate.LocalChatRoom
import at.torch.compose.extensions.exporterSecret
import at.torch.compose.extensions.toHex
import at.torch.compose.nostr.Relays
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupData
import com.vitorpamplona.quartz.marmot.mip02Welcome.WelcomeEvent
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEvent
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEventEncryption
import com.vitorpamplona.quartz.marmot.mls.codec.TlsReader
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroup
import com.vitorpamplona.quartz.marmot.mls.messages.MlsKeyPackage
import com.vitorpamplona.quartz.marmot.mls.tree.Credential
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip40Expiration.expiration
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler
import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.io.bytestring.encode
import kotlin.io.encoding.Base64
import kotlin.time.Instant

@Dao
abstract class MarmotOutboundDao(
    private val database: TorchDatabase
) {
    val logger = Logger.withTag(TAG)

    @Transaction
    open suspend fun createMlsDirectMessage(
        userPublicKey: HexKey,
        peerPublicKey: HexKey,
        peerKeyPackage: MarmotKeyPackage,
        relays: List<String> = Relays.DefaultDMRelayList.map { it.url }
    ): String {
        // TODO: Might move this logic to a marmotOutboudDao for consistency...
        val groupId = RandomInstance.bytes(32).toHexKey()
        val mlsGroup = MlsGroup.create(
            identity = userPublicKey.hexToByteArray(),
            initialExtensions = listOf(
                MarmotGroupData.bootstrap(
                    nostrGroupId = groupId,
                    creatorPubKey = userPublicKey,
                    outboxRelays = relays,
                ).toExtension()
            )
        )

        // Save Chat Room
        val chatRoom = ChatRoom(
            id = groupId,
            userPublicKey = userPublicKey,
            mlsGroupState = mlsGroup.saveState().encodeTls().toHex(),
            subject = null,
        )
        database.chatRoomDao().upsert(
            chatRoom
        )

        // TODO: Save Participants...

        // Invite peer...
        inviteMember(
            groupId = groupId,
            mlsGroup = mlsGroup,
            userPublicKey = userPublicKey,
            peerPublicKey = peerPublicKey,
            peerKeyPackage = peerKeyPackage,
            relays = relays
        )

        // TODO: Update chatRoom
        database.chatRoomDao().upsert(
            chatRoom.copy(
                mlsGroupState = mlsGroup.saveState().encodeTls().toHex(),
            )
        )

        return groupId
    }

    private suspend fun inviteMember(
        groupId: HexKey,
        mlsGroup: MlsGroup,
        userPublicKey: HexKey,
        peerPublicKey: HexKey,
        peerKeyPackage: MarmotKeyPackage,
        relays: List<String> = Relays.DefaultDMRelayList.map { it.url }
    ) {
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

//        pushRetainedEpoch(nostrGroupId, retainedBefore)
//        persistGroup(nostrGroupId)

        // CommitEvent...
        val encryptedContent = encryptedCommitEvent(
            mlsGroup,
            commitBytes = commitResult.framedCommitBytes,
            exporterKey = commitResult.preCommitExporterSecret
        )

        val template = GroupEvent.build(
            encryptedContentBase64 = encryptedContent,
            nostrGroupId = groupId
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

        database.broadcastNostrEventRequestDao().insert(
            relays.map {
                BroadcastNostrEventRequest(
                    nostrEventId = commitEvent.id,
                    relayURL = it
                )
            }
        )

        // Send welcome event to participant
        commitResult.welcomeBytes?.let { welcomeBytes ->
            val welcomeBase64 = Base64.encode(
                source = welcomeBytes
            )

            // GiftWrap...
            val welcomeEvent = WelcomeEvent.build(
                welcomeBase64 = welcomeBase64,
                keyPackageEventId = peerKeyPackage.id,
                relays = relays.map { NormalizedRelayUrl(it) },
                nostrGroupId = groupId,
            )
            // Compute welcomeEventId
            val welcomeEventId = EventHasher.hashId(
                pubKey = userPublicKey,
                kind = welcomeEvent.kind,
                tags = welcomeEvent.tags,
                createdAt = welcomeEvent.createdAt,
                content = welcomeEvent.content
            )

            GiftWrapPayload(
                id = welcomeEventId,
                publicKey = userPublicKey,
                chatRoomId = groupId,
                kind = welcomeEvent.kind,
                tags = welcomeEvent.tags,
                content = welcomeEvent.content,
                createdAt = Instant.fromEpochSeconds(welcomeEvent.createdAt)
            )

            // Save chatMessage for the invite...
            database.chatMessageDao().upsert(
                ChatMessage(
                    content = "Invited participant to chat", // use profile.humanReadable...
                    chatRoomId = groupId,
                    senderPublicKey = userPublicKey,
                    isUserMessage = true, // TODO: This is information message...
                    giftWrapPayloadId = welcomeEventId,
                    marmotGroupEventId = null,
                    marmotInnerEventId = null
                )
            )
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
    ) {
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
        logger.d("plainTextBytes: ${plainTextBytes.contentToString()}")

        // Step 1: MLS encrypt
        val mlsCipherText = mlsGroup.encrypt(plainTextBytes)
        logger.d("mlsCipherText: ${mlsCipherText.contentToString()}")

        // Step 2: Outer ChaCha20-Poly1305 encryption
        val exportKey = mlsGroup.exporterSecret()
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

        val chatMessageOrNull = database.chatMessageDao().getChatMessagesByMarmotInnerEventId(innerEvent.id)

        logger.d("chatMessageOrNull: $chatMessageOrNull")
        chatMessageOrNull?.let { chatMessage ->
            database.chatMessageNostrEventRelationDao().upsert(
                ChatMessageNostrEventRelation(
                    chatMessageId = chatMessage.id,
                    nostrEventId = groupEvent.id
                )
            )

            database.marmotGroupEventDao().upsert(
                MarmotGroupEvent(
                    id = groupEvent.id,
                    createdAt = Instant.fromEpochSeconds(groupEvent.createdAt),
                    encryptedContent = groupEvent.encryptedContent(),
                    chatRoomId = localChatRoom.chatRoom.id,
                    nostrEventId = groupEvent.id,
                    userPublicKey = nostrSignerSync.pubKey,
                    publicKey = groupEvent.pubKey
                )
            )
            database.chatMessageDao().upsert(
                chatMessage.copy(
                    marmotGroupEventId = groupEvent.id
                )
            )

            database.marmotInnerEventDao().upsert(
                marmotInnerEvent.copy(
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
    }

    companion object {
        const val TAG = "MarmotOutboundDao"
    }
}