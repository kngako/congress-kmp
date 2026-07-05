package at.torch.compose.repository

import at.torch.compose.database.model.MarmotKeyPackage
import at.torch.compose.database.model.Participant
import at.torch.compose.database.model.Profile
import at.torch.compose.database.model.intermdiate.LocalChatMessage
import at.torch.compose.database.model.intermdiate.LocalChatRoom
import at.torch.compose.database.model.intermdiate.LocalProfile
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

    suspend fun getChatRoomByIdentifier(id: HexKey): LocalChatRoom?

    suspend fun getAllParticipantsWithPubKey(publicKey: HexKey): List<Participant>

    suspend fun getChatMessageListByChatRoomId(chatRoomId: String): List<LocalChatMessage>

    suspend fun observeChatMessageListByChatRoomId(chatRoomId: String): Flow<List<LocalChatMessage>>

    suspend fun getOrCreateChatRoom(
        chatRoomId: String,
        activeUserPublicKey: HexKey,
        relayHint: String?,
        defaultSubject: String?,
        mlsGroupState: String?
    ): LocalChatRoom?

    suspend fun getChatMessageRelayForPublicKey(publicKey: HexKey): ChatMessageRelayListEvent?

    suspend fun getMarmotKeyPackageForPublicKey(publicKey: HexKey): MarmotKeyPackage?

    suspend fun getProfileWithPublicKey(publicKey: HexKey): Profile?

    suspend fun sendChatMessage(
        text: String,
        localChatRoom: LocalChatRoom,
        messageType: Kind = ChatMessageEvent.KIND
    )

    fun updateChatRoomSubject(publicKey: String, subject: String): at.torch.compose.database.model.ChatRoom?

    suspend fun observeUnsealedGiftWrapPayloads(publicKey: HexKey): Flow<at.torch.compose.database.model.GiftWrapPayload?>

    suspend fun sealGiftWrapPayload(giftWrapPayload: at.torch.compose.database.model.GiftWrapPayload, nostrSignerSync: NostrSignerSync)

    companion object {
        val NO_OP_CHAT_REPOSITORY = object: ChatRepository {
            override suspend fun observeChatRoomListByPublicKey(publicKey: String): Flow<List<LocalChatRoom>> {
                return flow {  }
            }

            override suspend fun getChatRoomListByPublicKey(publicKey: String): List<LocalChatRoom> {
                return emptyList()
            }

            override suspend fun getChatRoomByIdentifier(id: HexKey): LocalChatRoom? {
                return null
            }

            override suspend fun getAllParticipantsWithPubKey(publicKey: HexKey): List<Participant> {
                TODO("Not yet implemented")
            }

            override suspend fun getChatMessageListByChatRoomId(chatRoomId: String): List<LocalChatMessage> {
                return emptyList()
            }

            override suspend fun observeChatMessageListByChatRoomId(chatRoomId: String): Flow<List<LocalChatMessage>> {
                return flow {
                }
            }

            override suspend fun getOrCreateChatRoom(
                chatRoomId: String,
                activeUserPublicKey: HexKey,
                relayHint: String?,
                defaultSubject: String?,
                mlsGroupState: String?
            ): LocalChatRoom? {
                return null
            }

            override suspend fun getChatMessageRelayForPublicKey(publicKey: HexKey): ChatMessageRelayListEvent? {
                return null
            }

            override suspend fun getMarmotKeyPackageForPublicKey(publicKey: HexKey): MarmotKeyPackage? {
                TODO("Not yet implemented")
            }

            override suspend fun getProfileWithPublicKey(publicKey: HexKey): Profile? {
                TODO("Not yet implemented")
            }

            override suspend fun sendChatMessage(
                text: String,
                localChatRoom: LocalChatRoom,
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