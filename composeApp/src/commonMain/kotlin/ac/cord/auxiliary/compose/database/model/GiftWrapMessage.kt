package ac.cord.auxiliary.compose.database.model

import ac.cord.auxiliary.compose.database.model.traits.LocalStoreEntity
import ac.cord.auxiliary.compose.database.model.traits.NostrEventEntity
import ac.cord.auxiliary.compose.database.model.traits.SoftDeletableEntity
import ac.cord.auxiliary.compose.database.model.traits.TimestampedEntity
import ac.cord.auxiliary.compose.database.model.traits.UserViewableEntity
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.PrimaryKey
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Kind
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealedRumorEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import fr.acinq.bitcoin.PrivateKey
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

    suspend fun decryptGiftWrapSeal(
        privateKey: ByteArray
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

            val nostrSigner = NostrSignerInternal(
                keyPair = KeyPair(privKey = privateKey)
            )

            giftWrapEvent.unwrapOrNull(
                nostrSigner
            )?.let { giftWrapSeal ->
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


        return null
    }
}