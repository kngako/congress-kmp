package press.mantra.compose.database.model.intermdiate

import androidx.room3.Embedded
import androidx.room3.Relation

data class LocalBroadcastNostrEventRequest(
    @Embedded val broadcastNostrEventRequest: press.mantra.compose.database.model.BroadcastNostrEventRequest,

    @Relation(
        parentColumns = ["nostrEventId"],
        entityColumns = ["id"]
    )
    val nostrEvent: press.mantra.compose.database.model.NostrEvent,
)
