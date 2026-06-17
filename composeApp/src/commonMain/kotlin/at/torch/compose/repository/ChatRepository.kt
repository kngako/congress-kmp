package at.torch.compose.repository

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Kind
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface ChatRepository {
    suspend fun observeChatRoomListByPublicKey(publicKey: String): Flow<List<at.torch.compose.database.model.intermdiate.LocalChatRoom>>

    suspend fun getChatRoomListByPublicKey(publicKey: String): List<at.torch.compose.database.model.intermdiate.LocalChatRoom>

    suspend fun getChatRoomByIdentifier(id: String): at.torch.compose.database.model.intermdiate.LocalChatRoom?

    suspend fun getChatMessageListByChatRoomId(chatRoomId: String): List<at.torch.compose.database.model.intermdiate.LocalChatMessage>

    suspend fun observeChatMessageListByChatRoomId(chatRoomId: String): Flow<List<at.torch.compose.database.model.intermdiate.LocalChatMessage>>

    suspend fun getOrCreateChatRoom(
        activeUserPublicKey: HexKey,
        publicKey: String, relayHint: String?,
        defaultSubject: String? = null
    ): at.torch.compose.database.model.intermdiate.LocalChatRoom?

    suspend fun getChatMessageRelayForPublicKey(publicKey: HexKey): ChatMessageRelayListEvent?

    suspend fun sendChatMessage(
        text: String,
        localChatRoom: at.torch.compose.database.model.intermdiate.LocalChatRoom,
        messageType: Kind = ChatMessageEvent.KIND
    )

    fun updateChatRoomSubject(publicKey: String, subject: String): at.torch.compose.database.model.ChatRoom?

    suspend fun observeUnsealedGiftWrapPayloads(publicKey: HexKey): Flow<at.torch.compose.database.model.GiftWrapPayload?>

    suspend fun sealGiftWrapPayload(giftWrapPayload: at.torch.compose.database.model.GiftWrapPayload, nostrSignerSync: NostrSignerSync)

    companion object {
        val NO_OP_CHAT_REPOSITORY = object: ChatRepository {
            override suspend fun observeChatRoomListByPublicKey(publicKey: String): Flow<List<at.torch.compose.database.model.intermdiate.LocalChatRoom>> {
                return flow {  }
            }

            override suspend fun getChatRoomListByPublicKey(publicKey: String): List<at.torch.compose.database.model.intermdiate.LocalChatRoom> {
                return emptyList()
            }

            override suspend fun getChatRoomByIdentifier(id: String): at.torch.compose.database.model.intermdiate.LocalChatRoom? {
                return null
            }

            override suspend fun getChatMessageListByChatRoomId(chatRoomId: String): List<at.torch.compose.database.model.intermdiate.LocalChatMessage> {
                return emptyList()
            }

            override suspend fun observeChatMessageListByChatRoomId(chatRoomId: String): Flow<List<at.torch.compose.database.model.intermdiate.LocalChatMessage>> {
                return flow {
                }
            }

            override suspend fun getOrCreateChatRoom(
                activeUserPublicKey: HexKey,
                publicKey: String,
                relayHint: String?,
                defaultSubject: String?
            ): at.torch.compose.database.model.intermdiate.LocalChatRoom? {
                return null
            }

            override suspend fun getChatMessageRelayForPublicKey(publicKey: HexKey): ChatMessageRelayListEvent? {
                return null
            }

            override suspend fun sendChatMessage(
                text: String,
                localChatRoom: at.torch.compose.database.model.intermdiate.LocalChatRoom,
                messageType: Kind
            ) {
                // TODO("Not yet implemented")
            }

            override fun updateChatRoomSubject(publicKey: String, subject: String): at.torch.compose.database.model.ChatRoom? {
                return null
            }

            override suspend fun observeUnsealedGiftWrapPayloads(publicKey: HexKey): Flow<at.torch.compose.database.model.GiftWrapPayload?> {
                TODO("Not yet implemented")
            }

            override suspend fun sealGiftWrapPayload(
                giftWrapPayload: at.torch.compose.database.model.GiftWrapPayload,
                nostrSignerSync: NostrSignerSync
            ) {
                TODO("Not yet implemented")
            }
        }
    }
}