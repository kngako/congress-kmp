package ac.cord.auxiliary.compose.database.model.intermdiate

import ac.cord.auxiliary.compose.database.model.Profile
import ac.cord.auxiliary.compose.database.model.Connection
import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class LocalFollowing(
    @Embedded val connection: Connection,

    @Relation(
        parentColumn = "sourcePublicKey",
        entityColumn = "publicKey",
    )
    val following: List<Profile>
)
