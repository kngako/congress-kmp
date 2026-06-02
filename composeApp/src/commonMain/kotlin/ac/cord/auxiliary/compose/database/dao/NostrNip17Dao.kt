package ac.cord.auxiliary.compose.database.dao

import ac.cord.auxiliary.compose.database.AuxDatabase
import ac.cord.auxiliary.compose.database.model.BroadcastNostrEventRequest
import ac.cord.auxiliary.compose.database.model.ChatRoom
import ac.cord.auxiliary.compose.database.model.GiftWrapMessage
import ac.cord.auxiliary.compose.database.model.GiftWrapSeal
import ac.cord.auxiliary.compose.database.model.NostrEvent
import ac.cord.auxiliary.compose.database.model.Participant
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalChatRoom
import ac.cord.auxiliary.compose.managers.SeedManager
import androidx.room3.Dao
import androidx.room3.Transaction
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealedRumorEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import kotlin.time.Instant


@Dao
abstract class NostrNip17Dao(
    private val database: AuxDatabase
) {
    val logger = Logger.withTag(TAG)

    @Transaction
    open suspend fun getOrCreateChatRoom(publicKey: String, defaultSubject: String? = null): LocalChatRoom? {
        logger.d("getOrCreateChatRoom: $publicKey")

        val activePublicKey = SeedManager.activePublicKey().toHexKey()

        val hexKeys = setOf(
            activePublicKey,
            publicKey
        )

        val chatRoomId = ChatRoom.deriveChatRoomId(hexKeys)
        logger.d("chatRoomId: $chatRoomId")
        val localChatRoom = database.chatRoomDao().findChatRoomById(chatRoomId)

        if (localChatRoom != null) {
            return localChatRoom
        } else {
            val profiles = database.profileDao().getProfileByPublicKeys(hexKeys.toList())

            if (profiles.isNotEmpty()) {
                // Create new chatRoom
                val chatRoom = ChatRoom(
                    id = chatRoomId,
                    userPublicKey = activePublicKey,
                    subject = defaultSubject,
                )
                database.chatRoomDao().upsert(chatRoom)

                database.participantDao().upsert(
                    profiles.map {
                        Participant(
                            participantPublicKey = it.publicKey,
                            chatRoomId = chatRoomId
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

        // TODO: Get ChatMessageRelayListEvent.KIND for receiverPublicKey.pubkey
//        receiverPublicKey.relayHint?.url?.let { url ->
//            database.broadcastNostrEventRequestDao().insert(
//                listOf(
//                    BroadcastNostrEventRequest(
//                        nostrEventId = nostrEvent.id,
//                        relayURL = url
//                    )
//                )
//            )
//        }


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
    }

    companion object {
        const val TAG = "NostrNip17Dao"
    }
}