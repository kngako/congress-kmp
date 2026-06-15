package ac.cord.auxiliary.compose.repository

import ac.cord.auxiliary.compose.database.model.ChatRoom
import ac.cord.auxiliary.compose.database.model.GiftWrapPayload
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalChatMessage
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalChatRoom
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Kind
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface ChatRepository {
    suspend fun observeChatRoomListByPublicKey(publicKey: String): Flow<List<LocalChatRoom>>

    suspend fun getChatRoomListByPublicKey(publicKey: String): List<LocalChatRoom>

    suspend fun getChatRoomByIdentifier(id: String): LocalChatRoom?

    suspend fun getChatMessageListByChatRoomId(chatRoomId: String): List<LocalChatMessage>

    suspend fun observeChatMessageListByChatRoomId(chatRoomId: String): Flow<List<LocalChatMessage>>

    suspend fun getOrCreateChatRoom(publicKey: String, relayHint: String?, defaultSubject: String? = null): LocalChatRoom?

    suspend fun getChatMessageRelayForPublicKey(publicKey: HexKey): ChatMessageRelayListEvent?

    suspend fun sendChatMessage(
        text: String,
        localChatRoom: LocalChatRoom,
        messageType: Kind = ChatMessageEvent.KIND
    )

    fun updateChatRoomSubject(publicKey: String, subject: String): ChatRoom?

    suspend fun observeUnsealedGiftWrapPayloads(publicKey: HexKey): Flow<GiftWrapPayload?>

    suspend fun sealGiftWrapPayload(giftWrapPayload: GiftWrapPayload, nostrSignerSync: NostrSignerSync)

    companion object {
        val NO_OP_CHAT_REPOSITORY = object: ChatRepository {
            override suspend fun observeChatRoomListByPublicKey(publicKey: String): Flow<List<LocalChatRoom>> {
                return flow {  }
            }

            override suspend fun getChatRoomListByPublicKey(publicKey: String): List<LocalChatRoom> {
                return emptyList()
            }

            override suspend fun getChatRoomByIdentifier(id: String): LocalChatRoom? {
                return null
            }

            override suspend fun getChatMessageListByChatRoomId(chatRoomId: String): List<LocalChatMessage> {
                return emptyList()
            }

            override suspend fun observeChatMessageListByChatRoomId(chatRoomId: String): Flow<List<LocalChatMessage>> {
                return flow {
                }
            }

            override suspend fun getOrCreateChatRoom(
                publicKey: String,
                relayHint: String?,
                defaultSubject: String?
            ): LocalChatRoom? {
                return null
            }

            override suspend fun getChatMessageRelayForPublicKey(publicKey: HexKey): ChatMessageRelayListEvent? {
                return null
            }

            override suspend fun sendChatMessage(
                text: String,
                localChatRoom: LocalChatRoom,
                messageType: Kind
            ) {
                // TODO("Not yet implemented")
            }

            override fun updateChatRoomSubject(publicKey: String, subject: String): ChatRoom? {
                return null
            }

            override suspend fun observeUnsealedGiftWrapPayloads(publicKey: HexKey): Flow<GiftWrapPayload?> {
                TODO("Not yet implemented")
            }

            override suspend fun sealGiftWrapPayload(
                giftWrapPayload: GiftWrapPayload,
                nostrSignerSync: NostrSignerSync
            ) {
                TODO("Not yet implemented")
            }
        }
    }
}