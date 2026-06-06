package ac.cord.auxiliary.compose.database.model

import ac.cord.auxiliary.compose.database.model.traits.LocalStoreEntity
import ac.cord.auxiliary.compose.database.model.traits.NostrEventEntity
import ac.cord.auxiliary.compose.database.model.traits.SoftDeletableEntity
import ac.cord.auxiliary.compose.database.model.traits.TimestampedEntity
import ac.cord.auxiliary.compose.database.model.traits.UserViewableEntity
import ac.cord.auxiliary.compose.network.serialization.encodeToJsonString
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Ignore
import androidx.room3.PrimaryKey
import co.touchlab.kermit.Logger
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Kind
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip44Encryption.Nip44
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealedRumorEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import fr.acinq.bitcoin.PrivateKey
import kotlin.math.log
import kotlin.time.Clock
import kotlin.time.Instant

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = NostrEvent::class,
            parentColumns = ["id"],
            childColumns = ["nostrEventId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class GiftWrapMessage(
    @PrimaryKey
    val id: HexKey,

    /**
     * Random PublicKey (should it be derived from randomPrivateKey?)
     */
    val publicKey: String,

    val kind: Kind = 1059,

    /**
     * Receiver Public Key Derived from tags
     *
     * [
     *     ["p", receiverPublicKey, "<relay-url>"] // receiver
     *   ]
     *
     */
    val receiverPublicKey: String,

    val receiverRelayHit: String?,

    /**
     *
     * encrypt(GiftWrapSeal, randomPrivateKey, receiverPublicKey)
     */
    val content: String,
    /**
     * Signed using the randomPrivateKey used to encrypt the GiftWrapSeal
     */
    val signature: String,

    /**
     * What gets broadcasts?
     */
    override val nostrEventId: String,

    /**
     * randomTimeUpTo2DaysInThePast
     */
    override val createdAt: Instant = Clock.System.now(),
    override val updatedAt: Instant = createdAt,
    override val savedAt: Instant = Clock.System.now(),
    override val viewedAt: Instant? = null,
    override val deletedAt: Instant? = null, // Who this message should be seen by (used to derive conversationId)
): NostrEventEntity, TimestampedEntity, LocalStoreEntity, UserViewableEntity, SoftDeletableEntity  {

    @Ignore
    private val logger = Logger.withTag("GiftWrapMessage")

    suspend fun decryptGiftWrapSeal(
        keyPair: KeyPair
    ): GiftWrapSeal? {
        if (kind == GiftWrapEvent.KIND) {
            val giftWrapEvent = GiftWrapEvent(
                id = id,
                pubKey = publicKey,
                tags = arrayOf(
                    PTag(
                        pubKey = receiverPublicKey,
                        relayHint = receiverRelayHit?.let { NormalizedRelayUrl(it) }
                    ).toTagArray()
                ),
                createdAt = createdAt.epochSeconds,
                content = content,
                sig = signature
            )
            logger.d("giftWrapEvent: ${giftWrapEvent.toJson()}")

            giftWrapEvent.recipientPubKey()?.let { recipientPublicKey ->
                logger.d("Recipient PublicKey: $recipientPublicKey")

                if (keyPair.pubKey.toHexKey() != recipientPublicKey) {
                    logger.e("We are unwrapping a message we may have sent from ${keyPair.pubKey.toHexKey()}")

                    keyPair.privKey?.let { privateKey ->
                        val sealJSON = Nip44.decrypt(
                            giftWrapEvent.content,
                            privateKey = privateKey,
                            pubKey = giftWrapEvent.pubKey.hexToByteArray()
                        )
                        logger.d("SealJSON: $sealJSON")

                        null
                    }
                } else {
                    val nostrSigner = NostrSignerInternal(
                        keyPair = KeyPair(privKey = keyPair.privKey)
                    )

                    giftWrapEvent.unwrapOrNull(
                        nostrSigner
                    )?.let { giftWrapSeal ->

                        logger.d("giftWrapSeal: ${giftWrapSeal.toJson()}")
                        return GiftWrapSeal(
                            id = giftWrapSeal.id,
                            publicKey = giftWrapSeal.pubKey,
                            content = giftWrapSeal.content,
                            tags = giftWrapSeal.tags,
                            createdAt = Instant.fromEpochSeconds(giftWrapSeal.createdAt),
                            signature = giftWrapSeal.sig
                        )
                    }
                }
            }
        }

        return null
    }
}