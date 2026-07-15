package press.mantra.compose.database.model

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import com.vitorpamplona.quartz.nip01Core.core.HexKey
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

    override val createdAt: Instant = Clock.System.now(),
    override val updatedAt: Instant = createdAt
): TimestampedEntity {

    fun toDialectEvent(): DialectEvent {
        return DialectEvent(
            id = "",
            pubKey = publicKey,
            createdAt = createdAt.epochSeconds,
            tags = emptyArray(),
            content = "",
            sig = ""
        )
    }

}
