package at.torch.compose.database.repository

import at.torch.compose.database.GENESIS_AT
import at.torch.compose.database.model.ChatMessage
import at.torch.compose.database.model.GiftWrapPayload
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Kind
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealedRumorEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

class DatabaseChatRepository(
    private val database: at.torch.compose.database.TorchDatabase,
    private val scope: CoroutineScope
): at.torch.compose.repository.ChatRepository {
    val logger = Logger.withTag(TAG)
    override suspend fun observeChatRoomListByPublicKey(publicKey: String): Flow<List<at.torch.compose.database.model.intermdiate.LocalChatRoom>> {
        return database.chatRoomDao().observeChatRoomListByUserPublicKey(publicKey)
    }

    override suspend fun getChatRoomListByPublicKey(publicKey: String): List<at.torch.compose.database.model.intermdiate.LocalChatRoom> {
        return database.chatRoomDao().getChatRoomListByUserPublicKey(publicKey)
    }

    override suspend fun getChatRoomByIdentifier(id: String): at.torch.compose.database.model.intermdiate.LocalChatRoom? {
        return database.chatRoomDao().findChatRoomById(id)
    }

    override suspend fun getChatMessageListByChatRoomId(chatRoomId: String): List<at.torch.compose.database.model.intermdiate.LocalChatMessage> {
        return database.chatMessageDao().getChatMessagesByChatRoomId(chatRoomId)
    }

    override suspend fun observeChatMessageListByChatRoomId(chatRoomId: String): Flow<List<at.torch.compose.database.model.intermdiate.LocalChatMessage>> {
        return database.chatMessageDao().observeChatMessagesByChatRoomId(chatRoomId)
    }


    override suspend fun getOrCreateChatRoom(
        activeUserPublicKey: HexKey,
        publicKey: String,
        relayHint: String?,
        defaultSubject: String?
    ): at.torch.compose.database.model.intermdiate.LocalChatRoom? = try {
        return database.nostrNip17Dao().getOrCreateChatRoom(
            activeUserPublicKey = activeUserPublicKey,
            publicKey = publicKey,
            relayHint = relayHint,
            defaultSubject = defaultSubject
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
            since = _root_ide_package_.ac.cord.compose.database.GENESIS_AT,
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

    override suspend fun sendChatMessage(
        text: String,
        localChatRoom: at.torch.compose.database.model.intermdiate.LocalChatRoom,
        messageType: Kind
    ) {
        logger.i("sendChatMessage($text): $localChatRoom")

        val receiverTags = localChatRoom.localParticipants.filter { participant ->
            participant.participant.participantPublicKey != localChatRoom.chatRoom.userPublicKey // TODO: Allow this where it's notes to self...
        }.map { localParticipant ->
            PTag.assemble(
                localParticipant.participant.participantPublicKey,
                localParticipant.participant.relayHint?.let { NormalizedRelayUrl(it) }
            )
        }

        val giftWrapPayloadId = database.giftWrapPayloadDao().upsert(
            _root_ide_package_.ac.cord.compose.database.model.GiftWrapPayload(
                kind = messageType,
                tags = receiverTags.toTypedArray(),
                content = text,
                chatRoomId = localChatRoom.chatRoom.id,
                publicKey = localChatRoom.chatRoom.userPublicKey
            )
        )
        database.chatMessageDao().upsert(
            _root_ide_package_.ac.cord.compose.database.model.ChatMessage(
                content = text,
                chatRoomId = localChatRoom.chatRoom.id,
                senderPublicKey = localChatRoom.chatRoom.userPublicKey,
                isUserMessage = true,
                giftWrapPayloadId = giftWrapPayloadId
            )
        )
    }

    override fun updateChatRoomSubject(
        publicKey: String,
        subject: String
    ): at.torch.compose.database.model.ChatRoom? {
        // TODO: Update chatRoom with subject...
        return null
    }

    override suspend fun observeUnsealedGiftWrapPayloads(publicKey: HexKey): Flow<at.torch.compose.database.model.GiftWrapPayload?> {
        return database.giftWrapPayloadDao().observeUnsealedGiftWrapPayloads(publicKey)
    }

    override suspend fun sealGiftWrapPayload(
        giftWrapPayload: at.torch.compose.database.model.GiftWrapPayload,
        nostrSignerSync: NostrSignerSync
    ) {
        database.participantDao().findParticipantsByChatRoomId(giftWrapPayload.chatRoomId).forEachIndexed { index, participant ->
            val wrapperKeyPair = KeyPair()
            val wrapperSigner = NostrSignerSync(
                keyPair = wrapperKeyPair
            )

            val giftWrapPayloadEvent = nostrSignerSync.signNormal<Event>(
                createdAt = giftWrapPayload.createdAt.epochSeconds,
                kind = giftWrapPayload.kind,
                tags = giftWrapPayload.tags,
                content = giftWrapPayload.content
            )
            logger.d("GiftWrap Payload Event: ${giftWrapPayloadEvent.toJson()}")


            logger.d("Seal to $participant")
            val payload = giftWrapPayloadEvent.toJson()

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
                giftWrapPayload = Pair(
                    index,
                    giftWrapPayload
                ),
                sealedRumorEvent = sealedRumorEvent,
                giftWrapEvent = giftWrapEvent,
                receiverPublicKey = PTag(
                    pubKey = participant.participantPublicKey,
                    relayHint = participant.relayHint?.let { NormalizedRelayUrl(it) }
                )
            )
        }

    }

    companion object {
        const val TAG = "DatabaseChatRepository"
    }
}