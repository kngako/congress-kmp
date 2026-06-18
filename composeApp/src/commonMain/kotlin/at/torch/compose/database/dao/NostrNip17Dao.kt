package at.torch.compose.database.dao

import at.torch.compose.database.GENESIS_AT
import at.torch.compose.database.model.BroadcastNostrEventRequest
import at.torch.compose.database.model.ChatMessageBroadcastNostrEventRequestRelation
import at.torch.compose.database.model.ChatMessageNostrEventRelation
import at.torch.compose.database.model.ChatRoom
import at.torch.compose.database.model.GiftWrapMessage
import at.torch.compose.database.model.GiftWrapSeal
import at.torch.compose.database.model.NostrEvent
import at.torch.compose.database.model.Participant
import at.torch.compose.database.model.intermdiate.LocalChatRoom
import androidx.room3.Dao
import androidx.room3.Transaction
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealedRumorEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import kotlin.time.Instant


@Dao
abstract class NostrNip17Dao(
    private val database: at.torch.compose.database.TorchDatabase
) {
    val logger = Logger.withTag(TAG)

    @Transaction
    open suspend fun getOrCreateChatRoom(chatRoomId: String, activeUserPublicKey: HexKey, relayHint: String?, defaultSubject: String? = null, mlsGroupState: String? = null): LocalChatRoom? {
        logger.d("getOrCreateChatRoom: $chatRoomId")


        val hexKeys = setOf(
            activeUserPublicKey,
//            publicKey
        )

        val localChatRoom = database.chatRoomDao().findChatRoomById(chatRoomId)

        if (localChatRoom != null) {
            // TODO: might want to update the mlsGroupState?
            return localChatRoom
        } else {
            val profiles = database.profileDao().getProfileByPublicKeys(hexKeys.toList())

            if (profiles.isNotEmpty()) {
                // Create new chatRoom
                val chatRoom = ChatRoom(
                    id = chatRoomId,
                    userPublicKey = activeUserPublicKey,
                    subject = defaultSubject,
                    mlsGroupState = mlsGroupState
                )
                database.chatRoomDao().upsert(chatRoom)

                database.participantDao().upsert(
                    profiles.map {
                        Participant(
                            participantPublicKey = it.publicKey,
                            chatRoomId = chatRoomId,
                            relayHint = relayHint
                        )
                    }
                )

                return LocalChatRoom(
                    chatRoom = chatRoom
                )
            }
        }

        return null
    }

    @Transaction
    open suspend fun persistAndBroadcastGiftWrap(
        giftWrapPayload: Pair<Int, at.torch.compose.database.model.GiftWrapPayload>,
        sealedRumorEvent: SealedRumorEvent,
        giftWrapEvent: GiftWrapEvent,
        receiverPublicKey: PTag
    ) {
        val nostrEvent = NostrEvent(
            id = giftWrapEvent.id,
            content = giftWrapEvent.content,
            createdAt = Instant.fromEpochSeconds(giftWrapEvent.createdAt),
            sig = giftWrapEvent.sig,
            pubKey = giftWrapEvent.pubKey,
            tags = giftWrapEvent.tags,
            kind = giftWrapEvent.kind
        )
        logger.d("NostrEvent: $nostrEvent")


        database.nostrEventDao().insert(nostrEvent)

        val chatMessage = database.chatMessageDao().getChatMessagesByGiftWrapPayloadId(giftWrapPayload.second.id)

        chatMessage?.let {
            database.chatMessageNostrEventRelationDao().upsert(
                ChatMessageNostrEventRelation(
                    chatMessageId = it.id,
                    nostrEventId = nostrEvent.id
                )
            )
        }

        // Get ChatMessageRelayListEvent.KIND for receiverPublicKey.pubkey
        val chatMessageRelayListEvents = database.nostrEventDao().getAuthoredNostrEvents(
            kinds = arrayOf(ChatMessageRelayListEvent.KIND),
            authors = arrayOf(
                receiverPublicKey.pubKey
            ),
            since = GENESIS_AT,
            limit = 5
        )
        logger.d("Found messageRelayLists: $chatMessageRelayListEvents")

        chatMessageRelayListEvents.firstOrNull()?.let { chatMessageRelayListNostrEvent ->
            val chatMessageRelayListEvent = ChatMessageRelayListEvent(
                id = chatMessageRelayListNostrEvent.id,
                pubKey = chatMessageRelayListNostrEvent.pubKey,
                tags = chatMessageRelayListNostrEvent.tags,
                content = chatMessageRelayListNostrEvent.content,
                sig = chatMessageRelayListNostrEvent.sig,
                createdAt = chatMessageRelayListNostrEvent.createdAt.epochSeconds,
            )

            logger.d("Broadcast to ${chatMessageRelayListEvent.relays()}")

            // Only broadcasting to the first 3 relays...
            chatMessageRelayListEvent.relays().take(3).forEach { relayUrl ->
                logger.d("Submit BroadcastNostrEventRequest to $relayUrl")
                val broadcastNostrEventRequestIds = database.broadcastNostrEventRequestDao().insert(
                    listOf(
                        BroadcastNostrEventRequest(
                            nostrEventId = nostrEvent.id,
                            relayURL = relayUrl.url
                        )
                    )
                )

                chatMessage?.let {
                    broadcastNostrEventRequestIds.forEach {
                        database.chatMessageBroadcastNostrEventRequestRelationDao().upsert(
                            ChatMessageBroadcastNostrEventRequestRelation(
                                chatMessageId = chatMessage.id,
                                broadcastNostrEventRequestId = it
                            )
                        )
                    }
                }

            }
        }




        database.giftWrapMessageDao().upsert(
            GiftWrapMessage(
                id = giftWrapEvent.id,
                nostrEventId = nostrEvent.id,
                content = giftWrapEvent.content,
                createdAt = Instant.fromEpochSeconds(giftWrapEvent.createdAt),
                signature = giftWrapEvent.sig,
                publicKey = giftWrapEvent.pubKey,
                kind = giftWrapEvent.kind,
                receiverPublicKey = receiverPublicKey.pubKey,
                receiverRelayHit = receiverPublicKey.relayHint?.url
            )
        )

        database.giftWrapSealDao().upsert(
            GiftWrapSeal(
                id = sealedRumorEvent.id,
                publicKey = sealedRumorEvent.pubKey,
                content = sealedRumorEvent.content,
                kind = sealedRumorEvent.kind,
                tags = sealedRumorEvent.tags,
                signature = sealedRumorEvent.sig,
                giftWrapMessageId = giftWrapEvent.id
            )
        )
        logger.d("persistAndBroadcastGiftWrap done")

        if (giftWrapPayload.first == 0 && giftWrapPayload.second.giftWrapSealId == null) {
            // Only update the giftWrapPayload.giftWrapSealId on the first seal...
            database.giftWrapPayloadDao().upsert(
                giftWrapPayload.second.copy(
                    giftWrapSealId = sealedRumorEvent.id
                )
            )
            logger.d("GiftWrap now sealed: ${giftWrapPayload.second.id}")
        }
    }

    companion object {
        const val TAG = "NostrNip17Dao"
    }
}