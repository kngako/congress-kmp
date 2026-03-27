package ac.aux.compose.database.model.intermdiate

import ac.aux.compose.database.model.BroadcastNostrEventReceipt
import ac.aux.compose.database.model.BroadcastNostrEventRequest
import ac.aux.compose.database.model.NostrEvent
import ac.aux.compose.database.model.Profile
import ac.aux.compose.database.model.UnsignedNostrEvent
import androidx.room.Embedded
import androidx.room.Relation

data class LocalBroadcastNostrEventRequest(
    @Embedded val broadcastNostrEventRequest: BroadcastNostrEventRequest,

    @Relation(
        parentColumn = "nostrEventId",
        entityColumn = "id"
    )
    val nostrEvent: NostrEvent,
)
