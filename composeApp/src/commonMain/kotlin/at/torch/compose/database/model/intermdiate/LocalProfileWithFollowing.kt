package at.torch.compose.database.model.intermdiate

import androidx.room3.Embedded
import androidx.room3.Junction
import androidx.room3.Relation

data class LocalProfileWithFollowing(
    @Embedded val nostrEvent: at.torch.compose.database.model.NostrEvent,

    @Relation(
        parentColumns = ["pubKey"],
        entityColumns = ["publicKey"]
    )
    val profile: at.torch.compose.database.model.Profile,

    @Relation(
        parentColumns = ["pubKey"],
        entityColumns = ["publicKey"],
        associateBy = Junction(
            value = at.torch.compose.database.model.Connection::class,
            parentColumns = ["sourcePublicKey"],
            entityColumns = ["destinationPublicKey"]
        )
    )
    val following: List<at.torch.compose.database.model.Profile>
)
