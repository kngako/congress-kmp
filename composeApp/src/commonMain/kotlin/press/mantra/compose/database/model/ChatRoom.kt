package press.mantra.compose.database.model

import androidx.room3.Entity
import androidx.room3.Ignore
import androidx.room3.PrimaryKey
import press.mantra.compose.database.model.traits.LocalStoreEntity
import press.mantra.compose.database.model.traits.TimestampedEntity
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import fr.acinq.bitcoin.PrivateKey
import fr.acinq.bitcoin.PublicKey
import fr.acinq.bitcoin.crypto.musig2.Musig2
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * A group, as every member of it sees it.
 *
 * Only the group's own facts live here. What belongs to one member -- their MLS state,
 * when they joined, whether they have archived it -- is [ChatRoomMembership], keyed by
 * (`chatRoomId`, `userPublicKey`). The two were one row until v23, which made this both
 * the group and one profile's membership of it and meant a second profile joining the
 * same group wrote over the first's ratchet state.
 *
 * Keyed by [id] alone, which is what the nineteen tables referencing a room depend on:
 * most of them hold the group's signed work, which is nobody's in particular. See
 * [ChatRoomMembership] for why the room was split rather than its key widened.
 *
 * No foreign keys of its own any more. The ones it had -- onto `Profile` for the owning
 * profile and onto `GiftWrapPayload` for the wrap it was opened from -- were both facts
 * about a membership and went with it.
 */
@Entity
data class ChatRoom(
    /**
     * Derived from the participants...
     *
     * can aggregate them into one id like musig(participants...)
     */
    @PrimaryKey
    val id: String,

    /**
     * Optional retrieved from tags...
     *
     *  Any member can change the topic by simply submitting a new subject to an existing pubkey + p tags room.
     *
     *  There is no need to send subject in every message. The newest subject in the chat room is the subject of the conversation.
     */
    val subject: String?,

    val description: String?,

    override val createdAt: Instant = Clock.System.now(),
    override val updatedAt: Instant = createdAt,
    override val savedAt: Instant = createdAt,

    ): TimestampedEntity, LocalStoreEntity
{
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
