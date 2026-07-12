package at.torch.compose.repository

import at.torch.compose.database.model.ChatRoom
import at.torch.compose.database.model.GiftWrapPayload
import at.torch.compose.database.model.MarmotKeyPackage
import at.torch.compose.database.model.Participant
import at.torch.compose.database.model.Profile
import at.torch.compose.database.model.intermdiate.LocalChatMessage
import at.torch.compose.database.model.intermdiate.LocalChatRoom
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
        description: String?,
        mlsGroupState: String?
    ): LocalChatRoom?

    suspend fun getChatMessageRelayForPublicKey(publicKey: HexKey): ChatMessageRelayListEvent?

    suspend fun getMarmotKeyPackageForPublicKey(publicKey: HexKey): MarmotKeyPackage?

    suspend fun createMlsDirectMessage(
        name: String? = null,
        description: String? = null,
        userPublicKey: HexKey,
        peerPublicKey: HexKey,
        peerKeyPackage: MarmotKeyPackage
    ): String

    suspend fun inviteMember(
        localChatRoom: LocalChatRoom,
        peerPublicKey: HexKey,
        peerKeyPackage: MarmotKeyPackage,
    )

    suspend fun sendChatMessage(
        text: String,
        localChatRoom: LocalChatRoom,
        messageType: Kind = ChatMessageEvent.KIND
    )

    fun updateChatRoomSubject(publicKey: String, subject: String): ChatRoom?

    suspend fun observeUnsealedGiftWrapPayloads(publicKey: HexKey): Flow<GiftWrapPayload?>

    suspend fun sealGiftWrapPayload(giftWrapPayload: GiftWrapPayload, nostrSignerSync: NostrSignerSync)

    suspend fun leaveChatRoom(localChatRoom: LocalChatRoom)

    suspend fun softDeleteChatRoom(chatRoom: ChatRoom)

    suspend fun deleteChatRoom(chatRoom: ChatRoom)

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
                description: String?,
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

            override suspend fun createMlsDirectMessage(
                name: String?,
                description: String?,
                userPublicKey: HexKey,
                peerPublicKey: HexKey,
                peerKeyPackage: MarmotKeyPackage
            ): String {
                TODO("Not yet implemented")
            }

            override suspend fun inviteMember(
                localChatRoom: LocalChatRoom,
                peerPublicKey: HexKey,
                peerKeyPackage: MarmotKeyPackage
            ) {
                TODO("Not yet implemented")
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

            override suspend fun leaveChatRoom(localChatRoom: LocalChatRoom) {
                TODO("Not yet implemented")
            }

            override suspend fun softDeleteChatRoom(chatRoom: ChatRoom) {
                TODO("Not yet implemented")
            }

            override suspend fun deleteChatRoom(chatRoom: ChatRoom) {
                TODO("Not yet implemented")
            }
        }
    }
}