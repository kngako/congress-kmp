package press.mantra.compose.database.model.intermdiate

import androidx.room3.Embedded
import androidx.room3.Relation

data class LocalAccount(
    @Embedded val unsignedNostrEvent: press.mantra.compose.database.model.UnsignedNostrEvent?,

    @Relation(
        parentColumns = ["id"],
        entityColumns = ["unsignedNostrEventId"]
    )
    val nostrEvent: press.mantra.compose.database.model.NostrEvent?,

    @Relation(
        parentColumns = ["pubKey"],
        entityColumns = ["publicKey"]
    )
    val profile: press.mantra.compose.database.model.Profile?,

    @Relation(
        parentColumns = ["id"],
        entityColumns = ["unsignedNostrEventId"]
    )
    val broadcastNostrEventRequest: press.mantra.compose.database.model.BroadcastNostrEventRequest?,

    @Relation(
        parentColumns = ["id"],
        entityColumns = ["unsignedNostrEventId"]
    )
    val broadcastNostrEventReceipt: press.mantra.compose.database.model.BroadcastNostrEventReceipt?,

    @Relation(
        parentColumns = ["id"],
        entityColumns = ["unsignedNostrEventId"]
    )
    val synchronizeNostrEventRequests: List<press.mantra.compose.database.model.SynchronizeNostrEventRequest>,

    )
