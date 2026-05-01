package ac.cord.auxiliary.compose.database.model.intermdiate

import ac.cord.auxiliary.compose.database.model.Connection
import ac.cord.auxiliary.compose.database.model.NostrEvent
import ac.cord.auxiliary.compose.database.model.Profile
import androidx.room3.Embedded
import androidx.room3.Junction
import androidx.room3.Relation

data class LocalProfileWithFollowers(
    @Embedded val nostrEvent: NostrEvent,

    @Relation(
        parentColumn = "pubKey",
        entityColumn = "publicKey"
    )
    val profile: Profile,

    @Relation(
        parentColumn = "pubKey",
        entityColumn = "publicKey",
        associateBy = Junction(
            value = Connection::class,
            parentColumn = "destinationPublicKey",
            entityColumn = "sourcePublicKey"
        )
    )
    val followers: List<Profile>
)
