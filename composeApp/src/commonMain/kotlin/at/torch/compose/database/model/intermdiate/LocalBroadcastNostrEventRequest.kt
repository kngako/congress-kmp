package at.torch.compose.database.model.intermdiate

import androidx.room3.Embedded
import androidx.room3.Relation

data class LocalBroadcastNostrEventRequest(
    @Embedded val broadcastNostrEventRequest: at.torch.compose.database.model.BroadcastNostrEventRequest,

    @Relation(
        parentColumns = ["nostrEventId"],
        entityColumns = ["id"]
    )
    val nostrEvent: at.torch.compose.database.model.NostrEvent,
)
