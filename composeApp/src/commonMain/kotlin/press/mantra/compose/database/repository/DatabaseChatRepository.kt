package press.mantra.compose.database.repository

import press.mantra.compose.database.GENESIS_AT
import press.mantra.compose.database.MantraDatabase
import press.mantra.compose.database.model.ChatMessage
import press.mantra.compose.database.model.ChatRoom
import press.mantra.compose.database.model.GiftWrapPayload
import press.mantra.compose.database.model.MarmotInnerEvent
import press.mantra.compose.database.model.MarmotKeyPackage
import press.mantra.compose.database.model.Participant
import press.mantra.compose.database.model.intermdiate.LocalChatMessage
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.marmot.mip02Welcome.WelcomeEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Kind
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealedRumorEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock
import kotlin.time.Instant

class DatabaseChatRepository(
    private val database: MantraDatabase,
    private val scope: CoroutineScope
): press.mantra.compose.repository.ChatRepository {
    val logger = Logger.withTag(TAG)
    override suspend fun observeChatRoomListByPublicKey(publicKey: String): Flow<List<LocalChatRoom>> {
        return database.chatRoomDao().observeChatRoomListByUserPublicKey(publicKey)
    }

    override suspend fun getChatRoomListByPublicKey(publicKey: String): List<LocalChatRoom> {
        return database.chatRoomDao().getChatRoomListByUserPublicKey(publicKey)
    }

    override suspend fun getChatRoomByIdentifier(id: HexKey): LocalChatRoom? {
        return database.chatRoomDao().findChatRoomById(id)
    }

    override suspend fun getAllParticipantsWithPubKey(publicKey: HexKey): List<Participant> {
        return database.participantDao().findParticipantByPublicKey(publicKey)
    }
    override suspend fun getChatMessageListByChatRoomId(chatRoomId: String): List<LocalChatMessage> {
        return database.chatMessageDao().getChatMessagesByChatRoomId(chatRoomId)
    }

    override suspend fun observeChatMessageListByChatRoomId(chatRoomId: String): Flow<List<LocalChatMessage>> {
        return database.chatMessageDao().observeChatMessagesByChatRoomId(chatRoomId)
    }


    override suspend fun getOrCreateChatRoom(
        chatRoomId: String,
        activeUserPublicKey: HexKey,
        relayHint: String?,
        defaultSubject: String?,
        description: String?,
        mlsGroupState: String?
    ): LocalChatRoom? = try {
        return database.nostrNip17Dao().getOrCreateChatRoom(
            chatRoomId = chatRoomId,
            activeUserPublicKey = activeUserPublicKey,
            relayHint = relayHint,
            defaultSubject = defaultSubject,
            description = description,
            mlsGroupState = mlsGroupState
        )
    } catch (e: Throwable) {
        logger.e("Error getting or creating chat", e)
        return null
    }

    override suspend fun getChatMessageRelayForPublicKey(publicKey: HexKey): ChatMessageRelayListEvent? {
        // TODO: Implement a UserChatMessageRelayList model
        return database.nostrEventDao().getAuthoredNostrEvents(
            kinds = arrayOf(
                ChatMessageRelayListEvent.KIND
            ),
            authors = arrayOf(
                publicKey
            ),
            since = GENESIS_AT,
            limit = 5
        ).firstOrNull()?.let { nostrEvent ->
            if (nostrEvent.kind != ChatMessageRelayListEvent.KIND) {
                logger.e("getAuthoredNostrEvents returned invalid ChatMessageRelayListEvent for ${publicKey}: $nostrEvent")
                null
            } else {
                ChatMessageRelayListEvent(
                    id = nostrEvent.id,
                    tags = nostrEvent.tags,
                    pubKey = nostrEvent.pubKey,
                    content = nostrEvent.content,
                    createdAt = nostrEvent.createdAt.epochSeconds,
                    sig = nostrEvent.sig
                )
            }
        }
    }

    override suspend fun getMarmotKeyPackageForPublicKey(publicKey: HexKey): MarmotKeyPackage? {
        return database.marmotKeyPackageDao().getMarmotKeyPackageForPublicKey(publicKey)
    }

    override suspend fun observeMarmotKeyPackageForPublicKey(publicKey: HexKey): Flow<MarmotKeyPackage?> {
        return database.marmotKeyPackageDao().observeMarmotKeyPackageForPublicKey(publicKey)
    }

    override suspend fun createNip17ChatRoom(
        userPublicKey: HexKey,
        participantPublicKeys: List<HexKey>,
        subject: String?,
        description: String?
    ): LocalChatRoom? = try {
        database.nostrNip17Dao().createNip17ChatRoom(
            userPublicKey = userPublicKey,
            participantPublicKeys = participantPublicKeys,
            subject = subject,
            description = description
        )
    } catch (e: Throwable) {
        logger.e("Error creating nip-17 chat room", e)
        null
    }

    override suspend fun createMlsDirectMessage(
        name: String?,
        description: String?,
        userPublicKey: HexKey,
        peerPublicKey: HexKey,
        peerKeyPackage: MarmotKeyPackage
    ): String {
        return database.marmotOutboundDao().createMlsDirectMessageChatRoom(
            name = name,
            description = description,
            userPublicKey = userPublicKey,
            peerPublicKey = peerPublicKey,
            peerKeyPackage = peerKeyPackage
        )
    }

    override suspend fun inviteMember(
        localChatRoom: LocalChatRoom,
        peerPublicKey: HexKey,
        peerKeyPackage: MarmotKeyPackage
    ) {
        database.marmotOutboundDao().inviteMemberToChatRoom(
            localChatRoom = localChatRoom,
            peerPublicKey = peerPublicKey,
            peerKeyPackage = peerKeyPackage
        )
    }
    override suspend fun addMembers(
        localChatRoom: LocalChatRoom,
        peers: List<Pair<HexKey, MarmotKeyPackage>>
    ): List<HexKey> = database.marmotOutboundDao().addMembersToChatRoom(
        localChatRoom = localChatRoom,
        peers = peers
    )

    override suspend fun sendChatMessage(
        text: String,
        localChatRoom: LocalChatRoom,
        messageType: Kind
    ) {
        logger.i("sendChatMessage($text): $localChatRoom")
        val mlsGroup = localChatRoom.chatRoom.toMlsGroup()

        if (mlsGroup != null) {
            // Create ChatRumor...
            val createdAt = Clock.System.now().epochSeconds
            val marmotInnerEventKind = if (messageType == ChatMessageEvent.KIND) {
                ChatEvent.KIND // nip-17 chatMessageEvent.kind is in MLS chatMessage.kind
            } else {
                messageType
            }

            val marmotInnerEventId = EventHasher.hashId(
                pubKey = localChatRoom.chatRoom.userPublicKey,
                createdAt = createdAt,
                tags = emptyArray(),
                content = text,
                kind = marmotInnerEventKind
            )

            database.marmotInnerEventDao().upsert(
                MarmotInnerEvent(
                    id = marmotInnerEventId,
                    publicKey = localChatRoom.chatRoom.userPublicKey,
                    createdAt = Instant.fromEpochSeconds(createdAt),
                    tags = emptyArray(),
                    content = text,
                    chatRoomId = localChatRoom.chatRoom.id,
                    kind = marmotInnerEventKind
                )
            )

            database.chatMessageDao().upsert(
                ChatMessage(
                    content = text,
                    chatRoomId = localChatRoom.chatRoom.id,
                    senderPublicKey = localChatRoom.chatRoom.userPublicKey,
                    isUserMessage = true,
                    giftWrapPayloadId = null,
                    marmotGroupEventId = null,
                    marmotInnerEventId = marmotInnerEventId,
                )
            )
        } else {
            // Send GiftWrapMessage
            val receiverTags = localChatRoom.localParticipants.filter { participant ->
                participant.participant.participantPublicKey != localChatRoom.chatRoom.userPublicKey // TODO: Allow this where it's notes to self...
            }.map { localParticipant ->
                PTag.assemble(
                    localParticipant.participant.participantPublicKey,
                    localParticipant.participant.relayHint?.let { NormalizedRelayUrl(it) }
                )
            }

            val createdAt = Clock.System.now().epochSeconds
            val giftWrapPayloadId = EventHasher.hashId(
                pubKey = localChatRoom.chatRoom.userPublicKey,
                createdAt = createdAt,
                tags = receiverTags.toTypedArray(),
                content = text,
                kind = messageType
            )

            database.giftWrapPayloadDao().upsert(
                GiftWrapPayload(
                    id = giftWrapPayloadId,
                    kind = messageType,
                    tags = receiverTags.toTypedArray(),
                    createdAt = Instant.fromEpochSeconds(createdAt),
                    content = text,
                    chatRoomId = localChatRoom.chatRoom.id,
                    publicKey = localChatRoom.chatRoom.userPublicKey
                )
            )

            database.chatMessageDao().upsert(
                ChatMessage(
                    content = text,
                    chatRoomId = localChatRoom.chatRoom.id,
                    senderPublicKey = localChatRoom.chatRoom.userPublicKey,
                    isUserMessage = true,
                    giftWrapPayloadId = giftWrapPayloadId,
                    marmotGroupEventId = null,
                    marmotInnerEventId = null
                )
            )
        }
    }

    override fun updateChatRoomSubject(
        publicKey: String,
        subject: String
    ): press.mantra.compose.database.model.ChatRoom? {
        // TODO: Update chatRoom with subject...
        return null
    }

    override suspend fun observeUnsealedGiftWrapPayloads(publicKey: HexKey): Flow<GiftWrapPayload?> {
        return database.giftWrapPayloadDao().observeUnsealedGiftWrapPayloads(publicKey)
    }

    override suspend fun sealGiftWrapPayload(
        giftWrapPayload: GiftWrapPayload,
        nostrSignerSync: NostrSignerSync
    ) {
        database.participantDao().findParticipantsByChatRoomId(giftWrapPayload.chatRoomId).forEach { participant ->
            if (giftWrapPayload.kind == WelcomeEvent.KIND) {
                logger.i("Only giftWrap welcomeEvent payload (${giftWrapPayload.id}) to the participant who published the related keyPackage")
                val welcomeEvent = WelcomeEvent(
                    id = giftWrapPayload.id,
                    pubKey = nostrSignerSync.pubKey,
                    tags = giftWrapPayload.tags,
                    content = giftWrapPayload.content,
                    createdAt = giftWrapPayload.createdAt.epochSeconds,
                    sig = ""
                )

                val shouldBeGiftWrapped: Boolean = welcomeEvent.keyPackageEventId()?.let { keyPackageEventId ->
                    val marmotKeyPackage = database.marmotKeyPackageDao().getMarmotKeyPackageById(
                        keyPackageEventId
                    )

                    logger.i("MarmoteKeyPackage published by ${marmotKeyPackage?.publicKey}")

                    marmotKeyPackage?.publicKey == participant.participantPublicKey
                } ?: false

                if (!shouldBeGiftWrapped) {
                    logger.w("Participant ${participant.participantPublicKey} didn't publish key package for this welcome event")

                    return@forEach
                }
            }

            val wrapperKeyPair = KeyPair()
            val wrapperSigner = NostrSignerSync(
                keyPair = wrapperKeyPair
            )

            // TODO: Don't sign the payload... use event template instead...
            val giftWrapPayloadRumor = RumorAssembler.assembleRumor(
                pubKey = nostrSignerSync.pubKey,
                ev = EventTemplate(
                    createdAt = giftWrapPayload.createdAt.epochSeconds,
                    kind = giftWrapPayload.kind,
                    tags = giftWrapPayload.tags,
                    content = giftWrapPayload.content
                )
            )
            logger.d("GiftWrap Payload Rumor: ${giftWrapPayloadRumor.toJson()}")

            logger.d("Seal to $participant")
            val payload = giftWrapPayloadRumor.toJson()

            val sealedRumorEvent = nostrSignerSync.signNormal<SealedRumorEvent>(
                createdAt = TimeUtils.randomWithTwoDays(),
                kind = SealedRumorEvent.KIND,
                tags = emptyArray(),
                content = nostrSignerSync.nip44Encrypt(
                    plaintext = payload,
                    toPublicKey = participant.participantPublicKey
                )
            )
            logger.d("SealedRumorEvent: ${sealedRumorEvent.toJson()}")

            val giftWrapEvent = wrapperSigner.signNormal<GiftWrapEvent>(
                createdAt = TimeUtils.randomWithTwoDays(),
                kind = GiftWrapEvent.KIND,
                tags = arrayOf(
                    PTag.assemble(participant.participantPublicKey, participant.relayHint?.let { NormalizedRelayUrl(it) })
                ),
                content = wrapperSigner.nip44Encrypt(
                    plaintext = sealedRumorEvent.toJson(),
                    toPublicKey = participant.participantPublicKey
                )
            )
            logger.d("giftWrapEvent: ${giftWrapEvent.toJson()}")

            database.nostrNip17Dao().persistAndBroadcastGiftWrap(
                giftWrapPayload = giftWrapPayload,
                sealedRumorEvent = sealedRumorEvent,
                giftWrapEvent = giftWrapEvent,
                receiverPublicKey = PTag(
                    pubKey = participant.participantPublicKey,
                    relayHint = participant.relayHint?.let { NormalizedRelayUrl(it) }
                )
            )
        }
    }

    override suspend fun leaveChatRoom(
        localChatRoom: LocalChatRoom,
        onCompletion: () -> Unit
    ) {
        database.chatRoomDao().upsert(
            chatRoom = localChatRoom.chatRoom.copy(
                leftGroupAt = Clock.System.now()
            )
        )

        // TODO: Process this in the notary...
        sendChatMessage(
            text = "Left Group",
            localChatRoom = localChatRoom,
            // TODO: Add Information/LeaveChatGroup Message Type...
        )

        onCompletion.invoke()
    }

    override suspend fun softDeleteChatRoom(
        chatRoom: ChatRoom,
        onCompletion: () -> Unit
    ) {
        database.chatRoomDao().upsert(
            chatRoom.copy(
                deletedAt = Clock.System.now()
            )
        )

        onCompletion.invoke()
    }

    override suspend fun deleteChatRoom(
        chatRoom: ChatRoom,
        onCompletion: () -> Unit
    ) {
        database.chatRoomDao().delete(chatRoom)

        onCompletion.invoke()
    }

    companion object {
        const val TAG = "DatabaseChatRepository"
    }
}