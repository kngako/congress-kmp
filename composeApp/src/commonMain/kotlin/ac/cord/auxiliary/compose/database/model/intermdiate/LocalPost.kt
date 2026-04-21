package ac.cord.auxiliary.compose.database.model.intermdiate

import ac.cord.auxiliary.compose.database.model.Post
import ac.cord.auxiliary.compose.database.model.Profile
import androidx.room.Embedded
import androidx.room.Relation

data class LocalPost(
    @Embedded val post: Post,
    @Relation(
        parentColumn = "profilePublicKey",
        entityColumn = "publicKey"
    )
    val profile: Profile
)
