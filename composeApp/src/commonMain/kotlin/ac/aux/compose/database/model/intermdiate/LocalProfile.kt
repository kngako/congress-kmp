package ac.aux.compose.database.model.intermdiate

import ac.aux.compose.database.model.BroadcastNostrEventReceipt
import ac.aux.compose.database.model.BroadcastNostrEventRequest
import ac.aux.compose.database.model.NostrEvent
import ac.aux.compose.database.model.Profile
import ac.aux.compose.database.model.SynchronizeNostrEventRequest
import ac.aux.compose.database.model.UnsignedNostrEvent
import androidx.room.Embedded
import androidx.room.Relation

data class LocalProfile(
    @Embedded val unsignedNostrEvent: UnsignedNostrEvent?,

    @Relation(
        parentColumn = "id",
        entityColumn = "unsignedNostrEventId"
    )
    val nostrEvent: NostrEvent?,

    @Relation(
        parentColumn = "pubKey",
        entityColumn = "publicKey"
    )
    val profile: Profile?,

    @Relation(
        parentColumn = "id",
        entityColumn = "unsignedNostrEventId"
    )
    val broadcastNostrEventRequest: BroadcastNostrEventRequest?,

    @Relation(
        parentColumn = "id",
        entityColumn = "unsignedNostrEventId"
    )
    val broadcastNostrEventReceipt: BroadcastNostrEventReceipt?,

    @Relation(
        parentColumn = "id",
        entityColumn = "unsignedNostrEventId"
    )
    val synchronizeNostrEventRequests: List<SynchronizeNostrEventRequest>,

)
