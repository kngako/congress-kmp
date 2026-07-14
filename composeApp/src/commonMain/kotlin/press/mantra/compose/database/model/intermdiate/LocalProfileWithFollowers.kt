package press.mantra.compose.database.model.intermdiate

import androidx.room3.Embedded
import androidx.room3.Junction
import androidx.room3.Relation

data class LocalProfileWithFollowers(
    @Embedded val nostrEvent: press.mantra.compose.database.model.NostrEvent,

    @Relation(
        parentColumns = ["pubKey"],
        entityColumns = ["publicKey"]
    )
    val profile: press.mantra.compose.database.model.Profile,

    @Relation(
        parentColumns = ["pubKey"],
        entityColumns = ["publicKey"],
        associateBy = Junction(
            value = press.mantra.compose.database.model.Connection::class,
            parentColumns = ["destinationPublicKey"],
            entityColumns = ["sourcePublicKey"]
        )
    )
    val followers: List<press.mantra.compose.database.model.Profile>
)
