package at.torch.compose.database.model

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Ignore
import androidx.room3.PrimaryKey
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import fr.acinq.bitcoin.PrivateKey
import fr.acinq.bitcoin.PublicKey
import fr.acinq.bitcoin.crypto.musig2.Musig2
import kotlin.time.Clock
import kotlin.time.Instant

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = Profile::class,
            parentColumns = ["publicKey"],
            childColumns = ["userPublicKey"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = GiftWrapPayload::class,
            parentColumns = ["id"],
            childColumns = ["initialGiftWrapPayloadId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ChatRoom(
    /**
     * Derived from the participants...
     *
     * can aggregate them into one id like musig(participants...)
     */
    @PrimaryKey
    val id: String,

    /**
     * Logged in user PublicKey... should help us have multiple user support...
     */
    val userPublicKey: HexKey,

    /**
     * Optional retrieved from tags...
     *
     *  Any member can change the topic by simply submitting a new subject to an existing pubkey + p tags room.
     *
     *  There is no need to send subject in every message. The newest subject in the chat room is the subject of the conversation.
     */
    val subject: String?,

    val mlsGroupState: String?, // TODO: Can this be nullable???

    /**
     * Derived from kind14/15 tags
     * [
     *     ["p", "<receiver-1-pubkey>", "<relay-url>"],
     *     ["p", "<receiver-2-pubkey>", "<relay-url>"],
     *     // rest of tags...
     *   ]
     *
     *   TODO: Might want to add a relation for this...
     */
    // TODO: Add a LastChatMessageRelation
//    val lastChatMessageId: String? = null,
    // Might want to focus on the other last messages...
    val initialGiftWrapPayloadId: Long? = null,

    override val createdAt: Instant = Clock.System.now(),
    override val updatedAt: Instant = createdAt,
    override val savedAt: Instant = createdAt,
    override val viewedAt: Instant? = null,
    override val deletedAt: Instant? = null,

    ): at.torch.compose.database.model.traits.TimestampedEntity,
    at.torch.compose.database.model.traits.LocalStoreEntity,
    at.torch.compose.database.model.traits.UserViewableEntity,
    at.torch.compose.database.model.traits.SoftDeletableEntity {

    companion object {
        const val TAG = "ChatRoom"

        @Ignore
        val logger = Logger.withTag(TAG)

        fun deriveChatRoomId(hexKeySet: Set<String>): String {
            val hexKeys = hexKeySet.sorted()

            logger.d("hexKeys: $hexKeys")

            val publicKeys = hexKeys.map {

                val publicKey = PublicKey(
                    PublicKey.compress(it.hexToByteArray())
                )
                if (publicKey.isValid().not()) {
                    logger.w("$it is an invalid pubkey (not representing a point on the secp256k1 curve): $publicKey")
                    // Using it to derive a publicKey
                    val derivedPublicKey = PrivateKey(
                        publicKey.xOnly().value
                    ).publicKey()

                    logger.w("Derived $derivedPublicKey: ${derivedPublicKey.isValid()}")

                    derivedPublicKey
                } else {
                    publicKey
                }
            }
            logger.d("PublicKeys: $publicKeys")

            val chatRoomXOnlyPublicKey = Musig2.aggregateKeys(
                publicKeys
            )
            logger.d("ChatRoomXOnly: $chatRoomXOnlyPublicKey")

            return chatRoomXOnlyPublicKey.publicKey.toHex()
        }
    }
}