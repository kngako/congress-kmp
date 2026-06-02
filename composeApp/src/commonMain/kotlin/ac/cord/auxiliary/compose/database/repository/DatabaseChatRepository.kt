package ac.cord.auxiliary.compose.database.repository

import ac.cord.auxiliary.compose.database.AuxDatabase
import ac.cord.auxiliary.compose.database.model.ChatMessage
import ac.cord.auxiliary.compose.database.model.ChatRoom
import ac.cord.auxiliary.compose.database.model.GiftWrapPayload
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalChatMessage
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalChatRoom
import ac.cord.auxiliary.compose.repository.ChatRepository
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Kind
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.people.taggedUsers
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealedRumorEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

class DatabaseChatRepository(
    private val database: AuxDatabase,
    private val scope: CoroutineScope
): ChatRepository {
    val logger = Logger.withTag(TAG)

    override suspend fun getChatRoomListByPublicKey(publicKey: String): List<LocalChatRoom> {
        return database.chatRoomDao().getChatRoomListByUserPublicKey(publicKey)
    }

    override suspend fun getChatRoomByIdentifier(id: String): LocalChatRoom? {
        return database.chatRoomDao().findChatRoomById(id)
    }

    override suspend fun getChatMessageListByChatRoomId(chatRoomId: String): List<LocalChatMessage> {
        return database.chatMessageDao().getChatMessagesByChatRoomId(chatRoomId)
    }

    override suspend fun observeChatMessageListByChatRoomId(chatRoomId: String): Flow<List<LocalChatMessage>> {
        return database.chatMessageDao().observeChatMessagesByChatRoomId(chatRoomId)
    }


    override suspend fun getOrCreateChatRoom(publicKey: String, defaultSubject: String?): LocalChatRoom? = try {
        return database.nostrNip17Dao().getOrCreateChatRoom(
            publicKey,
            defaultSubject = defaultSubject
        )
    } catch (e: Throwable) {
        logger.e("Error getting or creating chat", e)
        return null
    }

    override suspend fun sendChatMessage(
        text: String,
        localChatRoom: LocalChatRoom,
        messageType: Kind
    ) {
        logger.i("sendChatMessage($text): $localChatRoom")

        val receiverTags = localChatRoom.participants.filter { participant ->
            participant.participant.participantPublicKey != localChatRoom.chatRoom.userPublicKey
        }.map { participant ->
            PTag.assemble(participant.participant.participantPublicKey, null) // TODO: Get the relays associated with the participant...
        }

        val giftWrapPayloadId = database.giftWrapPayloadDao().upsert(
            GiftWrapPayload(
                kind = messageType,
                tags = receiverTags.toTypedArray(), // TODO: Get participants...
                content = text,
                publicKey = localChatRoom.chatRoom.userPublicKey
            )
        )
        database.chatMessageDao().upsert(
            ChatMessage(
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
    ): ChatRoom? {
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
        val giftWrapPayloadEvent = nostrSignerSync.signNormal<Event>(
            createdAt = giftWrapPayload.createdAt.epochSeconds,
            kind = giftWrapPayload.kind,
            tags = giftWrapPayload.tags,
            content = giftWrapPayload.content
        )

        giftWrapPayload.tags.taggedUsers().forEachIndexed {  index, receiverPublicKey ->
            val payload = giftWrapPayloadEvent.toJson()

            val sealedRumorEvent = nostrSignerSync.signNormal<SealedRumorEvent>(
                createdAt = TimeUtils.randomWithTwoDays(),
                kind = SealedRumorEvent.KIND,
                tags = emptyArray(),
                content = nostrSignerSync.nip44Encrypt(
                    plaintext = payload,
                    toPublicKey = receiverPublicKey.pubKey
                )
            )

            val giftWrapEvent = nostrSignerSync.signNormal<GiftWrapEvent>(
                createdAt = TimeUtils.randomWithTwoDays(),
                kind = GiftWrapEvent.KIND,
                tags = arrayOf(
                    PTag.assemble(receiverPublicKey.pubKey, receiverPublicKey.relayHint)
                ),
                content = nostrSignerSync.nip44Encrypt(
                    plaintext = sealedRumorEvent.toJson(),
                    toPublicKey = receiverPublicKey.pubKey
                )
            )

            database.nostrNip17Dao().persistAndBroadcastGiftWrap(
                sealedRumorEvent = sealedRumorEvent,
                giftWrapEvent = giftWrapEvent,
                receiverPublicKey = receiverPublicKey
            )

            if (index == 0 && giftWrapPayload.giftWrapSealId == null) {
                // Only update the giftWrapPayload.giftWrapSealId on the first seal...
                database.giftWrapPayloadDao().upsert(
                    giftWrapPayload.copy(
                        giftWrapSealId = sealedRumorEvent.id
                    )
                )
            }
        }
    }

    companion object {
        const val TAG = "DatabaseChatRepository"
    }
}