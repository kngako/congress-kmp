package ac.aux.compose.database.model

import ac.aux.compose.database.model.traits.NostrEventEntity
import ac.aux.compose.database.model.traits.UnsignedNostrEventEntity
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.vitorpamplona.quartz.nip01Core.core.HexKey

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = NostrEvent::class,
            parentColumns = ["id"],
            childColumns = ["nostrEventId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = UnsignedNostrEvent::class,
            parentColumns = ["id"],
            childColumns = ["unsignedNostrEventId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("nostrEventId"),
        Index("status"),
    ],
)
data class BroadcastNostrEventRequest(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    override val nostrEventId: HexKey,
    override val unsignedNostrEventId: Long? = null,
    val status: String = "pending",
    val relayURL: String,
): NostrEventEntity, UnsignedNostrEventEntity