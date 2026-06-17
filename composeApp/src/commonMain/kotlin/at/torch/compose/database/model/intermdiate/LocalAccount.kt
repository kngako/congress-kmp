package at.torch.compose.database.model.intermdiate

import androidx.room3.Embedded
import androidx.room3.Relation

data class LocalAccount(
    @Embedded val unsignedNostrEvent: at.torch.compose.database.model.UnsignedNostrEvent?,

    @Relation(
        parentColumns = ["id"],
        entityColumns = ["unsignedNostrEventId"]
    )
    val nostrEvent: at.torch.compose.database.model.NostrEvent?,

    @Relation(
        parentColumns = ["pubKey"],
        entityColumns = ["publicKey"]
    )
    val profile: at.torch.compose.database.model.Profile?,

    @Relation(
        parentColumns = ["id"],
        entityColumns = ["unsignedNostrEventId"]
    )
    val broadcastNostrEventRequest: at.torch.compose.database.model.BroadcastNostrEventRequest?,

    @Relation(
        parentColumns = ["id"],
        entityColumns = ["unsignedNostrEventId"]
    )
    val broadcastNostrEventReceipt: at.torch.compose.database.model.BroadcastNostrEventReceipt?,

    @Relation(
        parentColumns = ["id"],
        entityColumns = ["unsignedNostrEventId"]
    )
    val synchronizeNostrEventRequests: List<at.torch.compose.database.model.SynchronizeNostrEventRequest>,

    )
