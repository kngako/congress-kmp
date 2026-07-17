package press.mantra.compose.database.model

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import press.mantra.compose.database.model.traits.OptionalNostrEventEntity
import press.mantra.compose.database.model.traits.TimestampedEntity
import press.mantra.compose.nostr.nip30303.DialectEvent
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * 30304
 */
@Entity
data class MantraDialect(
    @PrimaryKey
    val id: HexKey,
    val publicKey: HexKey,
    val name: String,
    val country: String,
    val language: String,

    val signature: String,

    val relayHint: String? = null,
    val marmotGroupEventId: HexKey? = null,

    /**
     * Related nostrEventId if this is not a rumor...
     */
    override val nostrEventId: HexKey? = null,
    override val createdAt: Instant = Clock.System.now(),
    override val updatedAt: Instant = createdAt
): OptionalNostrEventEntity, TimestampedEntity {

    fun toDialectEvent(): DialectEvent {
        return DialectEvent(
            id = id,
            pubKey = publicKey,
            createdAt = createdAt.epochSeconds,
            tags = emptyArray(),
            content = "",
            sig = signature
        )
    }

}
