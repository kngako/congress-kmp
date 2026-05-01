package ac.cord.auxiliary.compose.database.model.intermdiate

import ac.cord.auxiliary.compose.database.model.BroadcastNostrEventReceipt
import ac.cord.auxiliary.compose.database.model.BroadcastNostrEventRequest
import ac.cord.auxiliary.compose.database.model.NostrEvent
import ac.cord.auxiliary.compose.database.model.Profile
import ac.cord.auxiliary.compose.database.model.SynchronizeNostrEventRequest
import ac.cord.auxiliary.compose.database.model.UnsignedNostrEvent
import androidx.room3.Embedded
import androidx.room3.Relation

data class LocalAccount(
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
