package ac.aux.compose.database.model.intermdiate

import ac.aux.compose.database.model.BroadcastNostrEventReceipt
import ac.aux.compose.database.model.BroadcastNostrEventRequest
import ac.aux.compose.database.model.NostrEvent
import ac.aux.compose.database.model.Profile
import ac.aux.compose.database.model.UnsignedNostrEvent
import androidx.room.Embedded
import androidx.room.Relation

data class LocalProfile(
    @Embedded val unsignedNostrEvent: UnsignedNostrEvent,

    @Relation(
        parentColumn = "signedNostrEventId",
        entityColumn = "id"
    )
    val nostrEvent: NostrEvent?,

    @Relation(
        parentColumn = "pubKey",
        entityColumn = "publicKey"
    )
    val profile: Profile?,

    @Relation(
        parentColumn = "signedNostrEventId",
        entityColumn = "nostrEventId"
    )
    val broadcastNostrEventRequest: BroadcastNostrEventRequest?,

    @Relation(
        parentColumn = "signedNostrEventId",
        entityColumn = "nostrEventId"
    )
    val broadcastNostrEventReceipt: BroadcastNostrEventReceipt?
)
