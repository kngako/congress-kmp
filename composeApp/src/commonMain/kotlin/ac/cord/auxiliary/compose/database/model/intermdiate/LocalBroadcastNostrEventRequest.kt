package ac.cord.auxiliary.compose.database.model.intermdiate

import ac.cord.auxiliary.compose.database.model.BroadcastNostrEventRequest
import ac.cord.auxiliary.compose.database.model.NostrEvent
import androidx.room3.Embedded
import androidx.room3.Relation

data class LocalBroadcastNostrEventRequest(
    @Embedded val broadcastNostrEventRequest: BroadcastNostrEventRequest,

    @Relation(
        parentColumn = "nostrEventId",
        entityColumn = "id"
    )
    val nostrEvent: NostrEvent,
)
