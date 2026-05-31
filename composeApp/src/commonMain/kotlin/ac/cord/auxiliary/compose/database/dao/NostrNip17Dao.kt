package ac.cord.auxiliary.compose.database.dao

import ac.cord.auxiliary.compose.database.AuxDatabase
import ac.cord.auxiliary.compose.database.model.ChatRoom
import ac.cord.auxiliary.compose.database.model.Participant
import ac.cord.auxiliary.compose.database.model.intermdiate.LocalChatRoom
import ac.cord.auxiliary.compose.managers.SeedManager
import androidx.room3.Dao
import androidx.room3.Transaction
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import fr.acinq.bitcoin.PublicKey
import fr.acinq.bitcoin.crypto.musig2.Musig2
import kotlin.math.log

@Dao
abstract class NostrNip17Dao(
    private val database: AuxDatabase
) {
    val logger = Logger.withTag(TAG)

    @Transaction
    open suspend fun getOrCreateChatRoom(publicKey: String, defaultSubject: String? = null): LocalChatRoom? {
        logger.d("getOrCreateChatRoom: $publicKey")

        val activePublicKey = SeedManager.activePublicKey().toHexKey()

        val hexKeys = listOf(
            activePublicKey,
            publicKey
        ).sorted()

        logger.d("hexKeys: $hexKeys")

        val publicKeys = hexKeys.map {
            PublicKey(
                PublicKey.compress(it.hexToByteArray())
            )
        }
        logger.d("PublicKeys: $publicKeys")

        val chatRoomXOnlyPublicKey = Musig2.aggregateKeys(
            publicKeys
        )
        logger.d("ChatRoomXOnly: $chatRoomXOnlyPublicKey")

        val chatRoomId = chatRoomXOnlyPublicKey.publicKey.value.toHex()
        logger.d("chatRoomId: $chatRoomId")
        val localChatRoom = database.chatRoomDao().findChatRoomById(chatRoomId)

        if (localChatRoom != null) {
            return localChatRoom
        } else {
            val profiles = database.profileDao().getAllProfiles()

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

    companion object {
        const val TAG = "NostrNip17Dao"
    }
}