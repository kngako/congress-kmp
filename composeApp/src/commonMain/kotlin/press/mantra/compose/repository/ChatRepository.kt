package press.mantra.compose.repository

import press.mantra.compose.database.model.ChatRoom
import press.mantra.compose.database.model.GiftWrapPayload
import press.mantra.compose.database.model.MarmotKeyPackage
import press.mantra.compose.database.model.Participant
import press.mantra.compose.database.model.intermdiate.LocalChatMessage
import press.mantra.compose.database.model.intermdiate.LocalChatRoom
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

    suspend fun observeMarmotKeyPackageForPublicKey(publicKey: HexKey): Flow<MarmotKeyPackage?>

    /**
     * Creates a NIP-17 group: participants and a room, no MLS group. Membership is
     * the p-tag set carried by each message, so there is nothing to invite anyone to.
     */
    suspend fun createNip17ChatRoom(
        userPublicKey: HexKey,
        participantPublicKeys: List<HexKey>,
        subject: String? = null,
        description: String? = null
    ): LocalChatRoom?

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

    /**
     * Adds every member in one MLS commit, returning those that could not be added.
     *
     * Prefer this to looping [inviteMember] whenever the membership is known up
     * front: one commit means no intermediate epoch for a member to miss, which is
     * a silent failure rather than a loud one. See docs/marmot-membership.md.
     */
    suspend fun addMembers(
        localChatRoom: LocalChatRoom,
        peers: List<Pair<HexKey, MarmotKeyPackage>>,
    ): List<HexKey>

    /**
     * Sends [text] to the room, or -- when [directMessageRecipientPublicKey] is given --
     * to that one member of it.
     *
     * A direct message still travels as one group event that every member receives and
     * can see the shape of; only its contents are private. See
     * docs/marmot-direct-messages.md. NIP-17 rooms do not support it: there is no group
     * for the rest of to see it, so a private message there is just a message.
     */
    suspend fun sendChatMessage(
        text: String,
        localChatRoom: LocalChatRoom,
        messageType: Kind = ChatMessageEvent.KIND,
        directMessageRecipientPublicKey: HexKey? = null
    )

    fun updateChatRoomSubject(publicKey: String, subject: String): ChatRoom?

    suspend fun observeUnsealedGiftWrapPayloads(publicKey: HexKey): Flow<GiftWrapPayload?>

    suspend fun sealGiftWrapPayload(giftWrapPayload: GiftWrapPayload, nostrSignerSync: NostrSignerSync)

    suspend fun leaveChatRoom(
        localChatRoom: LocalChatRoom,
        onCompletion: () -> Unit
    )

    suspend fun softDeleteChatRoom(
        chatRoom: ChatRoom,
        onCompletion: () -> Unit
    )

    suspend fun deleteChatRoom(
        chatRoom: ChatRoom,
        onCompletion: () -> Unit
    )

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

            override suspend fun observeMarmotKeyPackageForPublicKey(publicKey: HexKey): Flow<MarmotKeyPackage?> {
                TODO("Not yet implemented")
            }

            override suspend fun createNip17ChatRoom(
                userPublicKey: HexKey,
                participantPublicKeys: List<HexKey>,
                subject: String?,
                description: String?
            ): LocalChatRoom? {
                return null
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

            override suspend fun addMembers(
                localChatRoom: LocalChatRoom,
                peers: List<Pair<HexKey, MarmotKeyPackage>>
            ): List<HexKey> = peers.map { it.first }


            override suspend fun sendChatMessage(
                text: String,
                localChatRoom: LocalChatRoom,
                messageType: Kind,
                directMessageRecipientPublicKey: HexKey?
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

            override suspend fun leaveChatRoom(
                localChatRoom: LocalChatRoom,
                onCompletion: () -> Unit
            ) {
                TODO("Not yet implemented")
            }

            override suspend fun softDeleteChatRoom(
                chatRoom: ChatRoom,
                onCompletion: () -> Unit
            ) {
                TODO("Not yet implemented")
            }

            override suspend fun deleteChatRoom(
                chatRoom: ChatRoom,
                onCompletion: () -> Unit
            ) {
                TODO("Not yet implemented")
            }
        }
    }
}